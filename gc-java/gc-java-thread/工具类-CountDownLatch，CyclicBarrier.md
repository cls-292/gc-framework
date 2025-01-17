
# 1. 倒计时器CountDownLatch #

在多线程协作完成业务功能时，有时候需要等待其他多个线程完成任务之后，主线程才能继续往下执行业务功能，在这种的业务场景下，通常可以使用Thread类的join方法，让主线程等待被join的线程执行完之后，主线程才能继续往下执行。当然，使用线程间消息通信机制也可以完成。其实，java并发工具类中为我们提供了类似“倒计时”这样的工具类，可以十分方便的完成所说的这种业务场景。

为了能够理解CountDownLatch，举一个很通俗的例子，运动员进行跑步比赛时，假设有6个运动员参与比赛，裁判员在终点会为这6个运动员分别计时，可以想象没当一个运动员到达终点的时候，对于裁判员来说就少了一个计时任务。直到所有运动员都到达终点了，裁判员的任务也才完成。这6个运动员可以类比成6个线程，当线程调用CountDownLatch.countDown方法时就会对计数器的值减一，直到计数器的值为0的时候，裁判员（调用await方法的线程）才能继续往下执行。

下面来看些CountDownLatch的一些重要方法。

先从CountDownLatch的构造方法看起：

	public CountDownLatch(int count)

构造方法会传入一个整型数N，之后调用CountDownLatch的`countDown`方法会对N减一，知道N减到0的时候，当前调用`await`方法的线程继续执行。

CountDownLatch的方法不是很多，将它们一个个列举出来：


1. await() throws InterruptedException：调用该方法的线程等到构造方法传入的N减到0的时候，才能继续往下执行；
2.  await(long timeout, TimeUnit unit)：与上面的await方法功能一致，只不过这里有了时间限制，调用该方法的线程等到指定的timeout时间后，不管N是否减至为0，都会继续往下执行；
3.  countDown()：使CountDownLatch初始值N减1；
4.   long getCount()：获取当前CountDownLatch维护的值；

下面用一个具体的例子来说明CountDownLatch的具体用法:

	public class CountDownLatchDemo {
	private static CountDownLatch startSignal = new CountDownLatch(1);
	//用来表示裁判员需要维护的是6个运动员
	private static CountDownLatch endSignal = new CountDownLatch(6);
	
	public static void main(String[] args) throws InterruptedException {
	    ExecutorService executorService = Executors.newFixedThreadPool(6);
	    for (int i = 0; i < 6; i++) {
	        executorService.execute(() -> {
	            try {
	                System.out.println(Thread.currentThread().getName() + " 运动员等待裁判员响哨！！！");
	                startSignal.await();
	                System.out.println(Thread.currentThread().getName() + "正在全力冲刺");
	                endSignal.countDown();
	                System.out.println(Thread.currentThread().getName() + "  到达终点");
	            } catch (InterruptedException e) {
	                e.printStackTrace();
	            }
	        });
	    }
	    System.out.println("裁判员发号施令啦！！！");
	    startSignal.countDown();
	    endSignal.await();
	    System.out.println("所有运动员到达终点，比赛结束！");
	    executorService.shutdown();
	}
	}
	输出结果：
	
	pool-1-thread-2 运动员等待裁判员响哨！！！
	pool-1-thread-3 运动员等待裁判员响哨！！！
	pool-1-thread-1 运动员等待裁判员响哨！！！
	pool-1-thread-4 运动员等待裁判员响哨！！！
	pool-1-thread-5 运动员等待裁判员响哨！！！
	pool-1-thread-6 运动员等待裁判员响哨！！！
	裁判员发号施令啦！！！
	pool-1-thread-2正在全力冲刺
	pool-1-thread-2  到达终点
	pool-1-thread-3正在全力冲刺
	pool-1-thread-3  到达终点
	pool-1-thread-1正在全力冲刺
	pool-1-thread-1  到达终点
	pool-1-thread-4正在全力冲刺
	pool-1-thread-4  到达终点
	pool-1-thread-5正在全力冲刺
	pool-1-thread-5  到达终点
	pool-1-thread-6正在全力冲刺
	pool-1-thread-6  到达终点
	所有运动员到达终点，比赛结束！

该示例代码中设置了两个CountDownLatch，第一个`endSignal`用于控制让main线程（裁判员）必须等到其他线程（运动员）让CountDownLatch维护的数值N减到0为止。另一个`startSignal`用于让main线程对其他线程进行“发号施令”，startSignal引用的CountDownLatch初始值为1，而其他线程执行的run方法中都会先通过 ` startSignal.await()`让这些线程都被阻塞，直到main线程通过调用`startSignal.countDown();`，将值N减1，CountDownLatch维护的数值N为0后，其他线程才能往下执行，并且，每个线程执行的run方法中都会通过`endSignal.countDown();`对`endSignal`维护的数值进行减一，由于往线程池提交了6个任务，会被减6次，所以`endSignal`维护的值最终会变为0，因此main线程在`latch.await();`阻塞结束，才能继续往下执行。

另外，需要注意的是，当调用CountDownLatch的countDown方法时，当前线程是不会被阻塞，会继续往下执行，比如在该例中会继续输出`pool-1-thread-4  到达终点`。


# 2. 循环栅栏：CyclicBarrier #

CyclicBarrier也是一种多线程并发控制的实用工具，和CountDownLatch一样具有等待计数的功能，但是相比于CountDownLatch功能更加强大。

为了理解CyclicBarrier，这里举一个通俗的例子。开运动会时，会有跑步这一项运动，我们来模拟下运动员入场时的情况，假设有6条跑道，在比赛开始时，就需要6个运动员在比赛开始的时候都站在起点了，裁判员吹哨后才能开始跑步。跑道起点就相当于“barrier”，是临界点，而这6个运动员就类比成线程的话，就是这6个线程都必须到达指定点了，意味着凑齐了一波，然后才能继续执行，否则每个线程都得阻塞等待，直至凑齐一波即可。cyclic是循环的意思，也就是说CyclicBarrier当多个线程凑齐了一波之后，仍然有效，可以继续凑齐下一波。CyclicBarrier的执行示意图如下：

![CyclicBarrier执行示意图.jpg](tool/CyclicBarrier执行示意图.jpg)



当多个线程都达到了指定点后，才能继续往下继续执行。这就有点像报数的感觉，假设6个线程就相当于6个运动员，到赛道起点时会报数进行统计，如果刚好是6的话，这一波就凑齐了，才能往下执行。**CyclicBarrier在使用一次后，下面依然有效，可以继续当做计数器使用，这是与CountDownLatch的区别之一。** 这里的6个线程，也就是计数器的初始值6，是通过CyclicBarrier的构造方法传入的。

下面来看下CyclicBarrier的主要方法：


	//等到所有的线程都到达指定的临界点
	await() throws InterruptedException, BrokenBarrierException 
	
	//与上面的await方法功能基本一致，只不过这里有超时限制，阻塞等待直至到达超时时间为止
	await(long timeout, TimeUnit unit) throws InterruptedException, 
	BrokenBarrierException, TimeoutException 

	//获取当前有多少个线程阻塞等待在临界点上
	int getNumberWaiting()
	
	//用于查询阻塞等待的线程是否被中断
	boolean isBroken()

		
	//将屏障重置为初始状态。如果当前有线程正在临界点等待的话，将抛出BrokenBarrierException。
	void reset()
	
另外需要注意的是，CyclicBarrier提供了这样的构造方法：

	public CyclicBarrier(int parties, Runnable barrierAction)

可以用来，当指定的线程都到达了指定的临界点的时，接下来执行的操作可以由barrierAction传入即可。


> 一个例子

下面用一个简单的例子，来看下CyclicBarrier的用法，我们来模拟下上面的运动员的例子。


	public class CyclicBarrierDemo {
	    //指定必须有6个运动员到达才行
	    private static CyclicBarrier barrier = new CyclicBarrier(6, () -> {
	        System.out.println("所有运动员入场，裁判员一声令下！！！！！");
	    });
	    public static void main(String[] args) {
	        System.out.println("运动员准备进场，全场欢呼............");
	
	        ExecutorService service = Executors.newFixedThreadPool(6);
	        for (int i = 0; i < 6; i++) {
	            service.execute(() -> {
	                try {
	                    System.out.println(Thread.currentThread().getName() + " 运动员，进场");
	                    barrier.await();
	                    System.out.println(Thread.currentThread().getName() + "  运动员出发");
	                } catch (InterruptedException e) {
	                    e.printStackTrace();
	                } catch (BrokenBarrierException e) {
	                    e.printStackTrace();
	                }
	            });
	        }
	    }
	
	}

	输出结果：
	运动员准备进场，全场欢呼............
	pool-1-thread-2 运动员，进场
	pool-1-thread-1 运动员，进场
	pool-1-thread-3 运动员，进场
	pool-1-thread-4 运动员，进场
	pool-1-thread-5 运动员，进场
	pool-1-thread-6 运动员，进场
	所有运动员入场，裁判员一声令下！！！！！
	pool-1-thread-6  运动员出发
	pool-1-thread-1  运动员出发
	pool-1-thread-5  运动员出发
	pool-1-thread-4  运动员出发
	pool-1-thread-3  运动员出发
	pool-1-thread-2  运动员出发

从输出结果可以看出，当6个运动员（线程）都到达了指定的临界点（barrier）时候，才能继续往下执行，否则，则会阻塞等待在调用`await()`处


# 3. CountDownLatch与CyclicBarrier的比较 #

 CountDownLatch与CyclicBarrier都是用于控制并发的工具类，都可以理解成维护的就是一个计数器，但是这两者还是各有不同侧重点的：

1. CountDownLatch一般用于某个线程A等待若干个其他线程执行完任务之后，它才执行；而CyclicBarrier一般用于一组线程互相等待至某个状态，然后这一组线程再同时执行；CountDownLatch强调一个线程等多个线程完成某件事情。CyclicBarrier是多个线程互等，等大家都完成，再携手共进。
2. 调用CountDownLatch的countDown方法后，当前线程并不会阻塞，会继续往下执行；而调用CyclicBarrier的await方法，会阻塞当前线程，直到CyclicBarrier指定的线程全部都到达了指定点的时候，才能继续往下执行；
3. CountDownLatch方法比较少，操作比较简单，而CyclicBarrier提供的方法更多，比如能够通过getNumberWaiting()，isBroken()这些方法获取当前多个线程的状态，**并且CyclicBarrier的构造方法可以传入barrierAction**，指定当所有线程都到达时执行的业务功能；
4. CountDownLatch是不能复用的，而CyclicLatch是可以复用的。