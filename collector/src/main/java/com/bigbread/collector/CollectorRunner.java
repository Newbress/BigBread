package com.bigbread.collector;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** {@code --collector.job=<이름>}으로 고른 작업을 한 번 실행하고 끝낸다. 이름이 없으면 사용법을 출력한다. */
@Component
public class CollectorRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CollectorRunner.class);

    private final CollectorProperties properties;
    private final List<CollectorJob> jobs;

    public CollectorRunner(CollectorProperties properties, List<CollectorJob> jobs) {
        this.properties = properties;
        this.jobs = jobs;
    }

    @Override
    public void run(ApplicationArguments args) {
        String requested = properties.job();
        if (requested == null || requested.isBlank()) {
            log.info("실행할 작업을 골라 주세요: --collector.job=<이름> [--collector.dry-run=true]");
            jobs.forEach(job -> log.info("  {} : {}", job.name(), job.description()));
            return;
        }
        CollectorJob job = jobs.stream().filter(j -> j.name().equals(requested)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("알 수 없는 작업이에요: " + requested
                        + " (가능한 작업: " + jobs.stream().map(CollectorJob::name).toList() + ")"));
        job.run(properties.dryRun());
    }
}
