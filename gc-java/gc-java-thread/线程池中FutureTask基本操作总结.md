
# 1.FutureTask简介 #

在Executors框架体系中，FutureTask用来表示可获取结果的异步任务。FutureTask实现了Future接口，FutureTask提供了启动和取消异步任务，查询异步任务是否计算结束以及获取最终的异步任务的结果的一些常用的方法。通过`get()`方法来获取异步任务的结果，但是会阻塞当前线程直至异步任务执行结束。一旦任务执行结束，任务不能重新启动或取消，除非调用`runAndReset()`方法。在FutureTask的源码中为其定义了这些状态：

	private static final int NEW          = 0;
	private static final int COMPLETING   = 1;
	private static final int NORMAL       = 2;
	private static final int EXCEPTIONAL  = 3;
	private static final int CANCELLED    = 4;
	private static final int INTERRUPTING = 5;
	private static final int INTERRUPTED  = 6;

另外，在《java并发编程的艺术》一书，作者根据FutureTask.run()方法的执行的时机，FutureTask分为了3种状态：


	
  1. **未启动**。FutureTask.run()方法还没有被执行之前，FutureTask处于未启动状态。当创建一个FutureTask，还没有执行FutureTask.run()方法之前，FutureTask处于未启动状态。
  2. **已启动**。FutureTask.run()方法被执行的过程中，FutureTask处于已启动状态。
  3.  **已完成**。FutureTask.run()方法执行结束，或者调用FutureTask.cancel(...)方法取消任务，或者在执行任务期间抛出异常，这些情况都称之为FutureTask的已完成状态。
 
下图总结了FutureTask的状态变化的过程：

![FutureTask状态迁移图.jpg](pool/FutureTask状态迁移图.jpg)


由于FutureTask具有这三种状态，因此执行FutureTask的get方法和cancel方法，当前处于不同的状态对应的结果也是大不相同。这里对get方法和cancel方法做个总结：

> get方法

当FutureTask处于未启动或已启动状态时，执行FutureTask.get()方法将导致调用线程阻塞。如果FutureTask处于已完成状态，调用FutureTask.get()方法将导致调用线程立即返回结果或者抛出异常

> cancel方法

当FutureTask处于**未启动状态**时，执行FutureTask.cancel()方法将此任务永远不会执行；

当FutureTask处于**已启动状态**时，执行FutureTask.cancel(true)方法将以中断线程的方式来阻止任务继续进行，如果执行FutureTask.cancel(false)将不会对正在执行任务的线程有任何影响；

当**FutureTask**处于已完成状态时，执行FutureTask.cancel(...)方法将返回false。

对Future的get()方法和cancel()方法用下图进行总结

![FutureTask的get和cancel的执行示意图.jpg](pool/FutureTask的get和cancel的执行示意图.jpg)


# 2. FutureTask的基本使用 #

FutureTask除了实现Future接口外，还实现了Runnable接口。因此，FutureTask可以交给Executor执行，也可以由调用的线程直接执行（FutureTask.run()）。另外，FutureTask的获取也可以通过ExecutorService.submit()方法返回一个FutureTask对象，然后在通过FutureTask.get()或者FutureTask.cancel方法。

**应用场景：** 当一个线程需要等待另一个线程把某个任务执行完后它才能继续执行，此时可以使用FutureTask。假设有多个线程执行若干任务，每个任务最多只能被执行一次。当多个线程试图执行同一个任务时，只允许一个线程执行任务，其他线程需要等待这个任务执行完后才能继续执行。

> 参考文献

《java并发编程的艺术》