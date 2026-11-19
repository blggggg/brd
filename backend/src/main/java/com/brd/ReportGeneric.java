package com.brd;

public abstract class ReportGeneric {
    public abstract byte[] runTasks(long companyId, int reportFormatId, String reportNo);
}
