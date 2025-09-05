package com.example.sample;

public class ExampleApp {
    private int counter = 0;

    public static void main(String[] args) throws Exception {
        ExampleApp app = new ExampleApp();
        app.run();
    }

    public void run() throws Exception {
        for (int i = 0; i < 3; i++) {
            int value = loopBody(i); // <- try line breakpoint here
            System.out.println("loop i=" + i + ", value=" + value);
            Thread.sleep(100); // slow it a bit for demo
        }
    }

    private int loopBody(int n) {
        counter += n;
        int f = fib(n + 5);
        String msg = "n=" + n + ", f=" + f + ", counter=" + counter;
        System.out.println(msg);
        return f;
    }

    private int fib(int k) {
        int a = 0, b = 1;
        for (int i = 0; i < k; i++) {
            int t = a + b;
            a = b;
            b = t;
        }
        return a;
    }
}
