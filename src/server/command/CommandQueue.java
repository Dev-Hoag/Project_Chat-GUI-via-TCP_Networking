package server.command;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

// Command queue - giữ thứ tự command và execute tuần tự trên một worker.
public class CommandQueue {
    private final Queue<Command> queue = new ConcurrentLinkedQueue<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "server-command-queue");
            thread.setDaemon(true);
            return thread;
        }
    });
    
    public void enqueue(Command cmd) {
        queue.offer(cmd);
        System.out.println("[COMMAND] Queued: " + cmd.getName());
    }
    
    public void executeAndQueue(Command cmd) {
        enqueue(cmd);
        executor.submit(() -> {
            try {
                cmd.execute();
                System.out.println("[COMMAND] Executed: " + cmd.getName());
            } catch (Exception e) {
                System.err.println("[COMMAND] Failed to execute: " + cmd.getName() + " - " + e.getMessage());
            } finally {
                queue.poll();
            }
        });
    }
    
    public int size() {
        return queue.size();
    }
}
