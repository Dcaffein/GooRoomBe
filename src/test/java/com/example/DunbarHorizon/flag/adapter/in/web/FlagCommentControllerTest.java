package com.example.DunbarHorizon.flag.adapter.in.web;

import com.example.DunbarHorizon.flag.application.dto.result.CommentResult;
import com.example.DunbarHorizon.flag.domain.comment.exception.FlagCommentNotFoundException;
import com.example.DunbarHorizon.support.BaseControllerTest;
import com.example.DunbarHorizon.support.WithMockCustomUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WithMockCustomUser
class FlagCommentControllerTest extends BaseControllerTest {

    private static final Long CURRENT_USER_ID = 1L;

    @Test
    @DisplayName("댓글 트리 조회 시 200을 반환하고 getCommentTree()를 호출한다")
    void getComments_Returns200() throws Exception {
        given(flagCommentQueryUseCase.getCommentTree(1L, CURRENT_USER_ID)).willReturn(List.of());

        mockMvc.perform(get("/api/v1/flags/1/comments"))
                .andExpect(status().isOk());

        verify(flagCommentQueryUseCase).getCommentTree(1L, CURRENT_USER_ID);
    }

    @Test
    @DisplayName("본인 댓글이면 응답에 isMine=true가 포함된다")
    void getComments_OwnComment_IsMineTrue() throws Exception {
        CommentResult comment = new CommentResult(
                10L,
                new CommentResult.WriterInfo(CURRENT_USER_ID, "작성자", null),
                "댓글 내용", false, LocalDateTime.now(), List.of(), true
        );
        given(flagCommentQueryUseCase.getCommentTree(1L, CURRENT_USER_ID)).willReturn(List.of(comment));

        mockMvc.perform(get("/api/v1/flags/1/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].isMine").value(true));
    }

    @Test
    @DisplayName("댓글 수 조회 시 200을 반환하고 getCommentCount()를 호출한다")
    void getCommentCount_Returns200() throws Exception {
        given(flagCommentQueryUseCase.getCommentCount(1L)).willReturn(5L);

        mockMvc.perform(get("/api/v1/flags/1/comments/count"))
                .andExpect(status().isOk());

        verify(flagCommentQueryUseCase).getCommentCount(1L);
    }

    @Test
    @DisplayName("루트 댓글 생성 시 201을 반환하고 createRootComment()를 호출한다")
    void createRootComment_Returns201() throws Exception {
        given(flagCommentCommandUseCase.createRootComment(eq(1L), eq(CURRENT_USER_ID), anyString(), anyBoolean()))
                .willReturn(10L);
        String body = """
                {"content": "댓글 내용", "isPrivate": false}
                """;

        mockMvc.perform(post("/api/v1/flags/1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        verify(flagCommentCommandUseCase).createRootComment(eq(1L), eq(CURRENT_USER_ID), eq("댓글 내용"), eq(false));
    }

    @Test
    @DisplayName("답글 생성 시 201을 반환하고 createReply()를 호출한다")
    void createReply_Returns201() throws Exception {
        given(flagCommentCommandUseCase.createReply(eq(1L), eq(2L), eq(CURRENT_USER_ID), anyString(), anyBoolean()))
                .willReturn(11L);
        String body = """
                {"content": "답글 내용", "isPrivate": false}
                """;

        mockMvc.perform(post("/api/v1/flags/1/comments/2/replies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        verify(flagCommentCommandUseCase).createReply(eq(1L), eq(2L), eq(CURRENT_USER_ID), eq("답글 내용"), eq(false));
    }

    @Test
    @DisplayName("댓글 수정 시 200을 반환하고 updateComment()를 호출한다")
    void updateComment_Returns200() throws Exception {
        String body = """
                {"content": "수정된 내용", "isPrivate": false}
                """;

        mockMvc.perform(patch("/api/v1/flags/1/comments/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        verify(flagCommentCommandUseCase).updateComment(1L, 2L, CURRENT_USER_ID, "수정된 내용", false);
    }

    @Test
    @DisplayName("댓글 삭제 시 204를 반환하고 deleteComment()를 호출한다")
    void deleteComment_Returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/flags/1/comments/2"))
                .andExpect(status().isNoContent());

        verify(flagCommentCommandUseCase).deleteComment(1L, 2L, CURRENT_USER_ID);
    }

    @Test
    @DisplayName("Comment가 경로의 Flag에 속하지 않으면 404를 반환한다")
    void updateComment_WrongFlag_Returns404() throws Exception {
        willThrow(new FlagCommentNotFoundException(2L))
                .given(flagCommentCommandUseCase)
                .updateComment(eq(999L), eq(2L), eq(CURRENT_USER_ID), anyString(), anyBoolean());

        mockMvc.perform(patch("/api/v1/flags/999/comments/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content": "수정 시도", "isPrivate": false}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Comment 최상위 URL은 더 이상 매핑되지 않는다")
    void legacyTopLevelCommentUrls_Return404() throws Exception {
        mockMvc.perform(delete("/api/v1/comments/1"))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/v1/comments/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content": "x", "isPrivate": false}
                                """))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/comments/1/replies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content": "x", "isPrivate": false}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Comment 내용이 500자를 넘으면 400을 반환한다")
    void createRootComment_ContentTooLong_Returns400() throws Exception {
        String tooLong = "가".repeat(501);
        String body = objectMapper.writeValueAsString(
                java.util.Map.of("content", tooLong, "isPrivate", false));

        mockMvc.perform(post("/api/v1/flags/1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validation.content").exists());
    }

    @Test
    @DisplayName("Comment 수정 내용이 500자를 넘으면 400을 반환한다")
    void updateComment_ContentTooLong_Returns400() throws Exception {
        String tooLong = "가".repeat(501);
        String body = objectMapper.writeValueAsString(
                java.util.Map.of("content", tooLong, "isPrivate", false));

        mockMvc.perform(patch("/api/v1/flags/1/comments/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validation.content").exists());
    }
}
