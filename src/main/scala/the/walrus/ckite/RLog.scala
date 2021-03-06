package the.walrus.ckite

import the.walrus.ckite.rpc.LogEntry
import the.walrus.ckite.rpc.WriteCommand
import java.util.concurrent.atomic.AtomicInteger
import the.walrus.ckite.util.Logging
import the.walrus.ckite.statemachine.StateMachine
import scala.collection.concurrent.TrieMap
import scala.collection.immutable.SortedSet
import the.walrus.ckite.rpc.AppendEntries
import the.walrus.ckite.rpc.EnterJointConsensus
import the.walrus.ckite.rpc.LeaveJointConsensus
import the.walrus.ckite.rpc.MajorityJointConsensus
import the.walrus.ckite.rpc.EnterJointConsensus
import the.walrus.ckite.rpc.ReadCommand
import the.walrus.ckite.rpc.Command
import java.util.concurrent.locks.ReentrantReadWriteLock
import org.mapdb.DBMaker
import java.io.File
import org.mapdb.DB
import the.walrus.ckite.rlog.FixedLogSizeCompactionPolicy
import the.walrus.ckite.rlog.Snapshot
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit
import com.twitter.concurrent.NamedPoolThreadFactory
import java.util.concurrent.SynchronousQueue
import the.walrus.ckite.util.CKiteConversions._
import the.walrus.ckite.rpc.NoOpWriteCommand

class RLog(cluster: Cluster, stateMachine: StateMachine, db: DB) extends Logging {

  val entries = db.getTreeMap[Int, LogEntry]("entries")
  val commitIndex = db.getAtomicInteger("commitIndex")
  
  val lastLog = new AtomicInteger(0)
  
  val lock = new ReentrantReadWriteLock()
  val exclusiveLock = lock.writeLock()
  val sharedLock = lock.readLock()
  
  val compactionPolicy = new FixedLogSizeCompactionPolicy(cluster.configuration.fixedLogSizeCompaction, db)
  
  val asyncApplierExecutor = new ThreadPoolExecutor(0, 1,
                                      10L, TimeUnit.SECONDS,
                                      new SynchronousQueue[Runnable](),
                                      new NamedPoolThreadFactory("AsyncApplierWorker", true))
  
  replay()
  
  private def replay() = {
    val lastSnapshot = getSnapshot()
    var startIndex = 1
    if (lastSnapshot.isDefined) {
        val snapshot = lastSnapshot.get
        startIndex = snapshot.lastLogEntryIndex + 1
        stateMachine.deserialize(snapshot.stateMachineState)
    }
    val currentCommitIndex = commitIndex.get()
    if (currentCommitIndex > 0) {
    	LOG.info(s"Start log replay from index $startIndex to $commitIndex")
    	startIndex to currentCommitIndex foreach { index => 
    	  LOG.info(s"Replaying index $index")
    	  execute(entries.get(index).command) 
    	}
    	LOG.info(s"Finished log replay")
    }
  }
  
  def getSnapshot(): Option[Snapshot] = {
    val snapshots = db.getTreeMap[Long,Array[Byte]]("snapshots")
    val lastSnapshot = snapshots.lastEntry()
    if (lastSnapshot != null) Some(Snapshot.deserialize(lastSnapshot.getValue())) else None
  }
  
  def tryAppend(appendEntries: AppendEntries) = {
    sharedLock.lock()
    try {
      LOG.trace(s"Try appending $appendEntries")
      val canAppend = hasPreviousLogEntry(appendEntries)
      if (canAppend) appendWithLockAcquired(appendEntries.entries)
      commitUntil(appendEntries.commitIndex)
      canAppend
    } finally {
      sharedLock.unlock()
      compactionPolicy.apply(this)
    }
    
  }
  
  private def hasPreviousLogEntry(appendEntries: AppendEntries) = {
    containsEntry(appendEntries.prevLogIndex, appendEntries.prevLogTerm)
  }

  private def appendWithLockAcquired(logEntries: List[LogEntry]) = {
    logEntries.foreach { logEntry =>
      LOG.info(s"Appending log entry $logEntry")
      entries.put(logEntry.index, logEntry)
      logEntry.command match {
        case c: EnterJointConsensus => cluster.apply(c)
        case c: LeaveJointConsensus => cluster.apply(c)
        case _ => Unit
      }
    }
  }

  def append(logEntries: List[LogEntry]) = {
    sharedLock.lock()
    try {
      appendWithLockAcquired(logEntries)
    } finally {
      sharedLock.unlock()
      compactionPolicy.apply(this)
    }
  }

  def getLogEntry(index: Int): Option[LogEntry] = {
    val entry = entries.get(index)
    if (entry != null) Some(entry) else None
  }
  
  def getLastLogEntry(): Option[LogEntry] = {
    getLogEntry(findLastLogIndex)
  }

  def getPreviousLogEntry(logEntry: LogEntry): Option[LogEntry] = {
    getLogEntry(logEntry.index - 1)
  }

  def containsEntry(index: Int, term: Int) = {
    val logEntryOption = getLogEntry(index)
    if (logEntryOption.isDefined) logEntryOption.get.term == term else (isZeroEntry(index,term) || isInSnapshot(index, term))
  }
  
  private def isZeroEntry(index: Int, term: Int): Boolean = index == -1 && term == -1
  
  private def isInSnapshot(index: Int, term: Int): Boolean = {
     getSnapshot().map{ snapshot =>  snapshot.lastLogEntryTerm <= term && snapshot.lastLogEntryIndex <= index }
     	.getOrElse(false).asInstanceOf[Boolean]
  }

  def commit(logEntry: LogEntry) = {
    val logEntryOption = getLogEntry(logEntry.index)
    if (logEntryOption.isDefined) {
      val entry = logEntryOption.get
    	if (entry.term == cluster.local.term) {
    		commitEntriesUntil(logEntry.index)
    		safeCommit(logEntry.index)
    	} else {
    		LOG.warn(s"Unsafe to commit an old term entry: $entry")
    	}
    }
  }

  private def commitEntriesUntil(entryIndex: Int) = {
    (commitIndex.intValue() + 1) until entryIndex foreach { index =>
      if (entries.containsKey(index)) {
        safeCommit(index)
      }
    }
  }

 private def commitUntil(leaderCommitIndex: Int) = {
    if (leaderCommitIndex > commitIndex.intValue()) {
      (commitIndex.intValue() + 1) to leaderCommitIndex foreach { index => safeCommit(index) }
    }
  }

  private def safeCommit(entryIndex: Int) = {
     val logEntryOption = getLogEntry(entryIndex)
    if (logEntryOption.isDefined) {
      val entry = logEntryOption.get
    	if (entryIndex > commitIndex.intValue()) {
    		LOG.info(s"Commiting entry $entry")
    		commitIndex.set(entry.index)
    		execute(entry.command)
    	} else {
    		LOG.info(s"Already commited entry $entry")
    	}
    }
  }

  def execute(command: Command) = {
    LOG.info(s"Executing $command")
    sharedLock.lock()
    try {
    	command match {
    	case c: EnterJointConsensus => {
    	   asyncApplierExecutor.execute(() => {
    		   cluster.on(MajorityJointConsensus(c.newBindings))
    	   })
    	   true
    	}
    	case c: LeaveJointConsensus => true
    	case c: NoOpWriteCommand => Unit
    	case _ => stateMachine.apply(command)
    	} 
    } finally {
      sharedLock.unlock()
    }
  }
  
  def execute(command: ReadCommand) = {
    stateMachine.apply(command)
  }

  def resetLastLog() = lastLog.set(findLastLogIndex)

  def findLastLogIndex(): Int = {
    if (entries.isEmpty) return 0
    entries.keySet.last()
  }

  def getCommitIndex(): Int = {
    commitIndex.intValue()
  }

  def nextLogIndex() = {
    lastLog.incrementAndGet()
  }
  
  def size() = entries.size
  
  def installSnapshot(snapshot: Snapshot): Boolean = {
    exclusiveLock.lock()
    try {
       LOG.info(s"Installing $snapshot")
       val snapshots = db.getTreeMap[Long,Array[Byte]]("snapshots")
       snapshots.put(System.currentTimeMillis(), snapshot.serialize)
       stateMachine.deserialize(snapshot.stateMachineState)
       commitIndex.set(snapshot.lastLogEntryIndex)
       LOG.info(s"Finished installing $snapshot")
       true
    } finally {
      exclusiveLock.unlock()
    }
    
  }
  
  def serializeStateMachine = stateMachine.serialize()

}