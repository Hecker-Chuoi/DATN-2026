package com.datn.workservice.notification;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MentionParserTest {

    private static final String AN = "14f45571-7ec4-4270-ab92-47d84cb3e774";
    private static final String BINH = "bda4676c-a23b-41fe-933e-421ec4cdad33";

    @Test
    void extractsMentionedIdsInOrderWithoutDuplicates() {
        String body = "@[An](" + AN + ") xem giúp, cc @[Bình Trần](" + BINH + ") và @[An](" + AN + ")";
        assertThat(MentionParser.mentionedUserIds(body)).containsExactly(UUID.fromString(AN), UUID.fromString(BINH));
    }

    @Test
    void ignoresMalformedMarkupAndPlainAtSigns() {
        assertThat(MentionParser.mentionedUserIds("email a@b.vn, @An, @[An](123), @[](" + AN + ")")).isEmpty();
        assertThat(MentionParser.mentionedUserIds(null)).isEmpty();
    }

    @Test
    void plainTextShowsDisplayNames() {
        assertThat(MentionParser.plainText("@[An](" + AN + ") ưu tiên khung 7h–9h"))
                .isEqualTo("@An ưu tiên khung 7h–9h");
        assertThat(MentionParser.plainText("giá $5 @[A$B](" + AN + ")")).isEqualTo("giá $5 @A$B");
    }
}
