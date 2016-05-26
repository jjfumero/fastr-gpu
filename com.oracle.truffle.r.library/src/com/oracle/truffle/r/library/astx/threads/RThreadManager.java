package com.oracle.truffle.r.library.astx.threads;

import java.util.ArrayList;

public class RThreadManager {

    public static RThreadManager INSTANCE = new RThreadManager();

    private ArrayList<Thread> threads;

    private RThreadManager() {
        threads = new ArrayList<>();
    }

    public void addThread(Thread thread) {
        threads.add(thread);
    }

    public Thread getThread(int idx) {
        return threads.get(idx);
    }

    public int getNumberOfPendingThreads() {
        return threads.size();
    }

    public void removeThread(int idx) {
        threads.remove(idx);
    }

    public ArrayList<Thread> getAll() {
        return threads;
    }
}
