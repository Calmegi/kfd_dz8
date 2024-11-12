package org.example

import java.util.LinkedList
import java.util.concurrent.Executor
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock


class ThreadPool(private val threadCount: Int) : Executor {
    private val tasks = LinkedList<Runnable>()
    private val threads: List<Thread>
    private val lock = ReentrantLock()
    private val condition: Condition = lock.newCondition()

    @Volatile
    private var isActive = true

    init {
        threads = List(threadCount) { index ->
            Thread {
                try {
                    while (true) {
                        val task: Runnable?

                        lock.lock()
                        try {
                            while (tasks.isEmpty() && isActive) {
                                condition.await()
                            }

                            if (tasks.isEmpty() && !isActive) {
                                break
                            }

                            task = tasks.removeFirst()
                        } finally {
                            lock.unlock()
                        }

                        try {
                            task?.run()
                        } catch (e: Exception) {
                            println("Исключение при выполнении задачи: ${e.message}")
                        }
                    }
                } catch (e: InterruptedException) {
                    println("Поток ${Thread.currentThread().name} был прерван.")
                }
            }.apply {
                name = "ThreadPool-Worker-$index"
                isDaemon = true
                start()
            }
        }
    }

    override fun execute(command: Runnable) {
        lock.lock()
        try {
            if (!isActive) {
                throw IllegalStateException("ThreadPool is shut down and not accepting new tasks.")
            }
            tasks.addLast(command)
            condition.signal()
        } finally {
            lock.unlock()
        }
    }

    fun shutdown(wait: Boolean) {
        lock.lock()
        try {
            isActive = false
            condition.broadcast()
        } finally {
            lock.unlock()
        }

        if (!wait) {
            threads.forEach { it.interrupt() }
        } else {
            threads.forEach { it.join() }
        }
    }
}

fun Condition.broadcast() {
    this.signalAll()
}

fun main() {
    val threadPool = ThreadPool(threadCount = 4)

    val tasks = List(10) { index ->
        Runnable {
            println("Задача $index выполняется поток ${Thread.currentThread().name}")
            Thread.sleep(1000)
            println("Задача $index завершена")
        }
    }

    for (task in tasks) {
        threadPool.execute(task)
    }

    Thread.sleep(5000)
    println("Инициируется shutdown пула с ожиданием завершения задач.")
    threadPool.shutdown(wait = true)
    println("Shutdown завершён.")

    val threadPoolImmediate = ThreadPool(threadCount = 2)

    for (task in tasks) {
        threadPoolImmediate.execute(task)
    }

    println("Инициируется shutdown пула без ожидания.")
    threadPoolImmediate.shutdown(wait = false)
    println("Shutdown завершён немедленно.")
}
