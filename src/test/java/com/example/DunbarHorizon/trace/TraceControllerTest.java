package com.example.DunbarHorizon.trace;

import com.example.DunbarHorizon.support.BaseControllerTest;
import com.example.DunbarHorizon.support.WithMockCustomUser;
import com.example.DunbarHorizon.trace.adapter.in.web.dto.VisitRequestDto;
import com.example.DunbarHorizon.trace.application.dto.TraceResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WithMockCustomUser(id = "1")
class TraceControllerTest extends BaseControllerTest {

    @Test
    @DisplayName("방문 시 reveal이 발생하지 않으면 revealed: false를 반환한다")
    void visitProfile_NotRevealed() throws Exception {
        Long targetId = 2L;
        VisitRequestDto requestDto = new VisitRequestDto(targetId);
        given(traceCommandUseCase.recordTrace(eq(1L), eq(targetId))).willReturn(TraceResult.notRevealed());

        mockMvc.perform(post("/api/v1/traces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revealed").value(false));

        verify(traceCommandUseCase).recordTrace(eq(1L), eq(targetId));
    }

    @Test
    @DisplayName("방문으로 reveal이 발생하면 revealed: true와 상대 유저 ID를 반환한다")
    void visitProfile_Revealed() throws Exception {
        Long targetId = 2L;
        VisitRequestDto requestDto = new VisitRequestDto(targetId);
        given(traceCommandUseCase.recordTrace(eq(1L), eq(targetId))).willReturn(new TraceResult(true));

        mockMvc.perform(post("/api/v1/traces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revealed").value(true));
    }
}
