package com.datn.workservice.service;

import com.datn.workservice.dto.response.BurndownResponse;
import com.datn.workservice.dto.response.CfdResponse;
import com.datn.workservice.dto.response.VelocityResponse;

import java.util.UUID;

public interface AgileReportService {

    /** Sprint da dong doc snapshot; Sprint dang chay dung lai tu changelog. */
    BurndownResponse getBurndown(UUID sprintId, Boolean excludeWeekends);

    /** N Sprint da dong gan nhat, so sanh cam ket voi hoan thanh. */
    VelocityResponse getVelocity(UUID projectId, Integer limit);

    /** So cong viec cong don theo nhom trang thai qua thoi gian. */
    CfdResponse getCfd(UUID projectId, UUID sprintId);
}
