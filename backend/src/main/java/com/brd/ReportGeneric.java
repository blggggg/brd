package com.brd;

public abstract class ReportGeneric {
    void runTasks(long companyId, int reportFormatId, String reportNo) {
        throw new UnsupportedOperationException("请在子类中实现 runTaks 方法");
    }
}
