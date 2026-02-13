package com.korolev.rps_game_server.net;

import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.EventExecutor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component("nettyEventLoop")
public class NettyEventLoopHealthIndicator implements HealthIndicator {

    private final NettyServer nettyServer;

    public NettyEventLoopHealthIndicator(NettyServer nettyServer) {
        this.nettyServer = nettyServer;
    }

    @Override
    public Health health() {
        EventLoopGroup boss = nettyServer.getBossGroup();
        EventLoopGroup worker = nettyServer.getWorkerGroup();

        Map<String, Object> details = new LinkedHashMap<>();
        if (boss == null || worker == null) {
            details.put("reason", "Netty not started");
            return Health.down().withDetails(details).build();
        }

        boolean bossShutting = boss.isShuttingDown();
        boolean workerShutting = worker.isShuttingDown();
        boolean bossTerminated = boss.isTerminated();
        boolean workerTerminated = worker.isTerminated();

        details.put("boss.shuttingDown", bossShutting);
        details.put("boss.terminated", bossTerminated);
        details.put("worker.shuttingDown", workerShutting);
        details.put("worker.terminated", workerTerminated);

        long start = System.nanoTime();
        boolean workerOk = checkGroup(worker, "worker", details, start);
        boolean bossOk = checkGroup(boss, "boss", details, start);

        boolean up = !(bossShutting || workerShutting || bossTerminated || workerTerminated) && workerOk && bossOk;

        return (up ? Health.up() : Health.down()).withDetails(details).build();
    }

    private boolean checkGroup(EventLoopGroup group, String prefix, Map<String, Object> details, long startNano) {
        if (group.isShuttingDown() || group.isTerminated()) {
            return false;
        }

        // Prevent deadlock if health check is somehow called from the event loop itself
        boolean alreadyInLoop = false;
        for (EventExecutor executor : group) {
            if (executor.inEventLoop()) {
                alreadyInLoop = true;
                break;
            }
        }
        if (alreadyInLoop) {
            details.put(prefix + ".inEventLoop", true);
            return true;
        }

        try {
            group.submit(() -> {
            }).get(500, TimeUnit.MILLISECONDS);
            long lagMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNano);
            details.put(prefix + ".lagMs", lagMs);
            if (lagMs > 100) {
                details.put(prefix + ".performance", "degraded");
            }
            details.put(prefix + ".canScheduleNoop", true);
            return true;
        } catch (Exception e) {
            details.put(prefix + ".error", e.getMessage());
            details.put(prefix + ".canScheduleNoop", false);
            return false;
        }
    }
}
