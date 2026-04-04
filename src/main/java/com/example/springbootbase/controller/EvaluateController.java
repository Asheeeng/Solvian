package com.example.springbootbase.controller;

import com.example.springbootbase.config.AuthInterceptor;
import com.example.springbootbase.dto.response.EvaluateResponse;
import com.example.springbootbase.model.SessionInfo;
import com.example.springbootbase.service.AiDiagnosisService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 诊断接口。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class EvaluateController {

    private final AiDiagnosisService aiDiagnosisService;

    @PostMapping(value = "/evaluate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EvaluateResponse evaluate(@RequestParam("file") MultipartFile file,
                                     @RequestParam(value = "isSocratic", required = false) Boolean isSocratic,
                                     @RequestParam(value = "is_socratic", required = false) Boolean isSocraticSnake,
                                     HttpServletRequest request) {
        SessionInfo sessionInfo = (SessionInfo) request.getAttribute(AuthInterceptor.CURRENT_SESSION_KEY);
        boolean finalSocratic = Boolean.TRUE.equals(isSocratic) || Boolean.TRUE.equals(isSocraticSnake);
        return aiDiagnosisService.evaluate(file, finalSocratic, sessionInfo);
    }
}
