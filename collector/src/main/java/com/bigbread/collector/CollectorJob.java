package com.bigbread.collector;

/** 수집기가 실행할 수 있는 작업 하나. {@code --collector.job=<name>}으로 고른다. */
public interface CollectorJob {

    String name();

    String description();

    void run(boolean dryRun);
}
