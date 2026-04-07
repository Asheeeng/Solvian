package com.example.springbootbase.controller;

import com.example.springbootbase.config.AuthInterceptor;
import com.example.springbootbase.dto.response.DiagnosisTaskResponse;
import com.example.springbootbase.model.SessionInfo;
import com.example.springbootbase.service.DiagnosisTaskService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 异步诊断任务接口。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DiagnosisTaskController {

    private final DiagnosisTaskService diagnosisTaskService;

    @PostMapping(value = "/diagnose", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DiagnosisTaskResponse createTask(@RequestParam("file") MultipartFile file,
                                            @RequestParam(value = "isSocratic", required = false) Boolean isSocratic,
                                            @RequestParam(value = "is_socratic", required = false) Boolean isSocraticSnake,
                                            @RequestParam(value = "problemType", required = false) String problemType,
                                            @RequestParam(value = "subjectScope", required = false) String subjectScope,
                                            HttpServletRequest request) {
        SessionInfo sessionInfo = (SessionInfo) request.getAttribute(AuthInterceptor.CURRENT_SESSION_KEY);
        boolean finalSocratic = Boolean.TRUE.equals(isSocratic) || Boolean.TRUE.equals(isSocraticSnake);
        String finalProblemType = (problemType != null && !problemType.isBlank())
                ? problemType
                : ((subjectScope != null && !subjectScope.isBlank()) ? subjectScope : "matrix");
        return diagnosisTaskService.createTask(file, finalSocratic, finalProblemType, sessionInfo);
    }

    @GetMapping("/diagnose/{taskId}")
    public DiagnosisTaskResponse getTask(@PathVariable("taskId") String taskId, HttpServletRequest request) {
        SessionInfo sessionInfo = (SessionInfo) request.getAttribute(AuthInterceptor.CURRENT_SESSION_KEY);
        return diagnosisTaskService.getTask(taskId, sessionInfo);
    }
}
