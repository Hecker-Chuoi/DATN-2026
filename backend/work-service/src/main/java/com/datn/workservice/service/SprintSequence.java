package com.datn.workservice.service;

import com.datn.workservice.entity.Sprint;
import com.datn.workservice.enums.SprintState;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

/**
 * Thu tu ke hoach cua cac Sprint / Giai doan trong mot du an (BR-S15).
 *
 * Cac giai doan duoc bat dau LAN LUOT: chi Sprint FUTURE dung dau theo ke hoach moi
 * duoc bat dau, va chi khi du an chua co Sprint ACTIVE. Thu tu ke hoach = ngay bat dau
 * du kien tang dan (Sprint chua co ngay xep cuoi), trung ngay thi Sprint tao truoc dung
 * truoc. Muon doi thu tu thi sua ngay bat dau du kien.
 *
 * Man hinh Backlog (BR-B02), co canStart va kiem tra trong startSprint() deu dung chung
 * lop nay, nen nut "Bat dau" sang dung o khoi FUTURE nam tren cung.
 */
public final class SprintSequence {

    private SprintSequence() {
    }

    /** Thu tu ke hoach, khong xet trang thai. Them id de ket qua luon on dinh. */
    public static final Comparator<Sprint> PLAN_ORDER = Comparator
            .comparing(Sprint::getStartDate, Comparator.nullsLast(Comparator.<LocalDateTime>naturalOrder()))
            .thenComparing(Sprint::getCreatedAt, Comparator.nullsLast(Comparator.<LocalDateTime>naturalOrder()))
            .thenComparing(Sprint::getId, Comparator.nullsLast(Comparator.<UUID>naturalOrder()));

    private static final Map<SprintState, Integer> STATE_WEIGHT = Map.of(
            SprintState.ACTIVE, 0, SprintState.FUTURE, 1, SprintState.CLOSED, 2);

    /** Thu tu hien thi: ACTIVE truoc, roi FUTURE, cuoi cung CLOSED; trong moi nhom theo ke hoach. */
    public static final Comparator<Sprint> DISPLAY_ORDER = Comparator
            .<Sprint, Integer>comparing(s -> STATE_WEIGHT.getOrDefault(s.getState(), 3))
            .thenComparing(PLAN_ORDER);

    /**
     * Sprint duy nhat duoc phep bat dau luc nay.
     *
     * @param sprints cac Sprint cua MOT du an; phai chua day du cac Sprint ACTIVE va FUTURE
     * @return id Sprint FUTURE dung dau ke hoach, hoac {@code null} neu du an dang co Sprint
     *         ACTIVE (BR-S11) hoac khong con Sprint FUTURE nao
     */
    public static UUID startableSprintId(Collection<Sprint> sprints) {
        if (sprints.stream().anyMatch(s -> s.getState() == SprintState.ACTIVE)) {
            return null;
        }
        return sprints.stream()
                .filter(s -> s.getState() == SprintState.FUTURE)
                .min(PLAN_ORDER)
                .map(Sprint::getId)
                .orElse(null);
    }
}
