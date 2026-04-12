package com.example.springbootbase.service.impl;

import com.example.springbootbase.auth.Role;
import com.example.springbootbase.dto.response.EvaluateResponse;
import com.example.springbootbase.model.ModelChainResult;
import com.example.springbootbase.model.PreprocessedImage;
import com.example.springbootbase.model.SessionInfo;
import com.example.springbootbase.service.AiDiagnosisService;
import com.example.springbootbase.service.ImagePreprocessService;
import com.example.springbootbase.service.ModelClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * AI 诊断编排实现（老师端矩阵题）。
 */
@Service
@RequiredArgsConstructor
public class AiDiagnosisServiceImpl implements AiDiagnosisService {

    private final ImagePreprocessService imagePreprocessService;
    private final ModelClientService modelClientService;
    private final DiagnosisResultComposer diagnosisResultComposer;

    @Override
    public EvaluateResponse evaluate(MultipartFile file, boolean isSocratic, String problemType, SessionInfo sessionInfo) {
        validateTeacherScope(sessionInfo);
        String subjectScope = normalizeSubjectScope(problemType);

        PreprocessedImage preprocessedImage = imagePreprocessService.preprocess(file);
        ModelChainResult chainResult = modelClientService.analyze(preprocessedImage, isSocratic, subjectScope);
        return diagnosisResultComposer.persistAndBuildResponse(
                sessionInfo,
                preprocessedImage,
                isSocratic,
                diagnosisResultComposer.compose(chainResult, subjectScope),
                subjectScope,
                null
        );
    }

    private void validateTeacherScope(SessionInfo sessionInfo) {
        if (sessionInfo == null) {
            throw new IllegalArgumentException("未登录，无法发起检测");
        }
        if (sessionInfo.getRole() != Role.TEACHER) {
            throw new IllegalArgumentException("当前阶段仅支持老师端检测");
        }
    }

    private String normalizeSubjectScope(String raw) {
        if (raw == null || raw.isBlank()) {
            return "matrix";
        }
        return "matrix";
    }
}
