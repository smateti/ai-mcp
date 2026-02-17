package com.example.jms;

import jakarta.enterprise.context.RequestScoped;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

@Path("/threads")
@RequestScoped
public class ThreadMonitorResource {

    private final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getThreadSummary() {
        ThreadInfo[] threads = threadMXBean.dumpAllThreads(false, false);

        int runnable = 0, waiting = 0, timedWaiting = 0, blocked = 0;
        for (ThreadInfo t : threads) {
            switch (t.getThreadState()) {
                case RUNNABLE -> runnable++;
                case WAITING -> waiting++;
                case TIMED_WAITING -> timedWaiting++;
                case BLOCKED -> blocked++;
                default -> { }
            }
        }

        JsonObjectBuilder json = Json.createObjectBuilder()
            .add("totalThreads", threads.length)
            .add("peakThreads", threadMXBean.getPeakThreadCount())
            .add("totalStarted", threadMXBean.getTotalStartedThreadCount())
            .add("runnable", runnable)
            .add("waiting", waiting)
            .add("timedWaiting", timedWaiting)
            .add("blocked", blocked);

        return Response.ok(json.build()).build();
    }

    @GET
    @Path("/blocked")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getBlockedThreads() {
        threadMXBean.setThreadContentionMonitoringEnabled(true);
        ThreadInfo[] threads = threadMXBean.dumpAllThreads(true, true);

        JsonArrayBuilder arr = Json.createArrayBuilder();
        for (ThreadInfo t : threads) {
            if (t.getThreadState() == Thread.State.BLOCKED ||
                t.getThreadState() == Thread.State.WAITING) {

                JsonObjectBuilder entry = Json.createObjectBuilder()
                    .add("name", t.getThreadName())
                    .add("id", t.getThreadId())
                    .add("state", t.getThreadState().name())
                    .add("blockedTimeMs", t.getBlockedTime())
                    .add("blockedCount", t.getBlockedCount())
                    .add("waitedTimeMs", t.getWaitedTime())
                    .add("waitedCount", t.getWaitedCount());

                if (t.getLockName() != null) {
                    entry.add("lockName", t.getLockName());
                }
                if (t.getLockOwnerName() != null) {
                    entry.add("lockOwner", t.getLockOwnerName())
                         .add("lockOwnerId", t.getLockOwnerId());
                }

                // Stack trace (top 5 frames)
                JsonArrayBuilder stack = Json.createArrayBuilder();
                StackTraceElement[] frames = t.getStackTrace();
                for (int i = 0; i < Math.min(5, frames.length); i++) {
                    stack.add(frames[i].toString());
                }
                entry.add("stackTrace", stack);
                arr.add(entry);
            }
        }

        var built = arr.build();
        return Response.ok(Json.createObjectBuilder()
            .add("blockedAndWaiting", built)
            .add("count", built.size())
            .build()).build();
    }

    @GET
    @Path("/deadlocks")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDeadlocks() {
        long[] deadlocked = threadMXBean.findDeadlockedThreads();

        if (deadlocked == null) {
            return Response.ok(Json.createObjectBuilder()
                .add("deadlocks", false)
                .add("message", "No deadlocks detected")
                .build()).build();
        }

        ThreadInfo[] threads = threadMXBean.getThreadInfo(deadlocked, true, true);
        JsonArrayBuilder arr = Json.createArrayBuilder();
        for (ThreadInfo t : threads) {
            JsonArrayBuilder stack = Json.createArrayBuilder();
            for (StackTraceElement frame : t.getStackTrace()) {
                stack.add(frame.toString());
            }
            arr.add(Json.createObjectBuilder()
                .add("name", t.getThreadName())
                .add("id", t.getThreadId())
                .add("state", t.getThreadState().name())
                .add("lockName", t.getLockName() != null ? t.getLockName() : "")
                .add("lockOwner", t.getLockOwnerName() != null ? t.getLockOwnerName() : "")
                .add("stackTrace", stack));
        }

        return Response.ok(Json.createObjectBuilder()
            .add("deadlocks", true)
            .add("count", deadlocked.length)
            .add("threads", arr)
            .build()).build();
    }

    @GET
    @Path("/long-waiting")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getLongWaitingThreads() {
        threadMXBean.setThreadContentionMonitoringEnabled(true);
        ThreadInfo[] threads = threadMXBean.dumpAllThreads(true, true);

        JsonArrayBuilder arr = Json.createArrayBuilder();
        for (ThreadInfo t : threads) {
            long totalWait = t.getBlockedTime() + t.getWaitedTime();
            long cpuTime = threadMXBean.getThreadCpuTime(t.getThreadId());
            long cpuMs = cpuTime / 1_000_000; // nano to ms

            // Flag threads where wait time >> cpu time (I/O bound / stuck)
            if (totalWait > 5000 && (cpuMs == 0 || totalWait / Math.max(cpuMs, 1) > 10)) {
                JsonArrayBuilder stack = Json.createArrayBuilder();
                StackTraceElement[] frames = t.getStackTrace();
                for (int i = 0; i < Math.min(5, frames.length); i++) {
                    stack.add(frames[i].toString());
                }
                arr.add(Json.createObjectBuilder()
                    .add("name", t.getThreadName())
                    .add("id", t.getThreadId())
                    .add("state", t.getThreadState().name())
                    .add("cpuTimeMs", cpuMs)
                    .add("blockedTimeMs", t.getBlockedTime())
                    .add("waitedTimeMs", t.getWaitedTime())
                    .add("totalWaitMs", totalWait)
                    .add("waitToCpuRatio", cpuMs > 0 ? totalWait / cpuMs : -1)
                    .add("stackTrace", stack));
            }
        }

        return Response.ok(Json.createObjectBuilder()
            .add("longWaitingThreads", arr)
            .build()).build();
    }
}
