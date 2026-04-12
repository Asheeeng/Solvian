package com.example.springbootbase.controller;

import com.example.springbootbase.config.AuthInterceptor;
import com.example.springbootbase.dto.response.DiagnosisTaskResponse;
import com.example.springbootbase.dto.response.SubmissionCreateResponse;
import com.example.springbootbase.dto.response.SubmissionListResponse;
import com.example.springbootbase.model.SessionInfo;
import com.example.springbootbase.service.DiagnosisTaskService;
import com.example.springbootbase.service.SubmissionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 学生作业提交接口。
 */
@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;
    private final DiagnosisTaskService diagnosisTaskService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SubmissionCreateResponse create(@RequestParam("file") MultipartFile file,
                                           HttpServletRequest request) {
        SessionInfo sessionInfo = (SessionInfo) request.getAttribute(AuthInterceptor.CURRENT_SESSION_KEY);
        return submissionService.createSubmission(file, sessionInfo);
    }

    @GetMapping("/mine")
    public SubmissionListResponse mine(@RequestParam(value = "limit", required = false, defaultValue = "3") Integer limit,
                                       HttpServletRequest request) {
        SessionInfo sessionInfo = (SessionInfo) request.getAttribute(AuthInterceptor.CURRENT_SESSION_KEY);
        return submissionService.listMine(sessionInfo, limit == null ? 3 : limit);
    }

    @GetMapping
    public SubmissionListResponse teacherList(@RequestParam(value = "limit", required = false) Integer limit,
                                              HttpServletRequest request) {
        SessionInfo sessionInfo = (SessionInfo) request.getAttribute(AuthInterceptor.CURRENT_SESSION_KEY);
        return submissionService.listForTeacher(sessionInfo, limit);
    }

    @GetMapping("/{submissionId}/image")
    public ResponseEntity<Resource> image(@PathVariable("submissionId") Long submissionId,
                                          HttpServletRequest request) {
        SessionInfo sessionInfo = (SessionInfo) request.getAttribute(AuthInterceptor.CURRENT_SESSION_KEY);
        Resource resource = submissionService.loadSubmissionImage(submissionId, sessionInfo);
        String contentType = submissionService.resolveImageContentType(submissionService.requireSubmission(submissionId));
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }

    @PostMapping("/{submissionId}/diagnose")
    public DiagnosisTaskResponse diagnose(@PathVariable("submissionId") Long submissionId,
                                          @RequestParam(value = "isSocratic", required = false) Boolean isSocratic,
                                          @RequestParam(value = "problemType", required = false) String problemType,
                                          HttpServletRequest request) {
        SessionInfo sessionInfo = (SessionInfo) request.getAttribute(AuthInterceptor.CURRENT_SESSION_KEY);
        return diagnosisTaskService.createTaskForSubmissionId(submissionId, Boolean.TRUE.equals(isSocratic), problemType, sessionInfo);
    }
}
