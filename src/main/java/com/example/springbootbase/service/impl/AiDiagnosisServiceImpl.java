package com.example.springbootbase.service.impl;

import com.example.springbootbase.dto.response.EvaluateResponse;
import com.example.springbootbase.model.DiagnosisRecord;
import com.example.springbootbase.model.DiagnosisResult;
import com.example.springbootbase.model.PreprocessedImage;
import com.example.springbootbase.model.SessionInfo;
import com.example.springbootbase.service.AiDiagnosisService;
import com.example.springbootbase.service.DiagnosisResultParser;
import com.example.springbootbase.service.ImagePreprocessService;
import com.example.springbootbase.service.MathValidationService;
import com.example.springbootbase.service.ModelClientService;
import com.example.springbootbase.store.InMemoryDataStore;
import com.example.springbootbase.util.IdUtil;
import com.example.springbootbase.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;

/**
 * AI 诊断编排实现。
 */
@Service
@RequiredArgsConstructor
public class AiDiagnosisServiceImpl implements AiDiagnosisService {

    private final ImagePreprocessService imagePreprocessService;
    private final ModelClientService modelClientService;
    private final DiagnosisResultParser diagnosisResultParser;
    private final MathValidationService mathValidationService;
    private final InMemoryDataStore store;

    @Override
    public EvaluateResponse evaluate(MultipartFile file, boolean isSocratic, SessionInfo sessionInfo) {
        PreprocessedImage preprocessedImage = imagePreprocessService.preprocess(file);

        String rawOutput = modelClientService.analyze(preprocessedImage, isSocratic);
        DiagnosisResult parsedResult = diagnosisResultParser.parse(rawOutput);
        DiagnosisResult validatedResult = mathValidationService.validate(parsedResult);

        String recordId = IdUtil.newId();
        DiagnosisRecord record = DiagnosisRecord.builder()
                .recordId(recordId)
                .userId(sessionInfo.getUserId())
                .username(sessionInfo.getUsername())
                .role(sessionInfo.getRole())
                .fileName(preprocessedImage.getFileName())
                .isSocratic(isSocratic)
                .status(validatedResult.getStatus())
                .steps(new ArrayList<>(validatedResult.getSteps()))
                .feedback(validatedResult.getFeedback())
                .errorIndex(validatedResult.getErrorIndex())
                .mathData(validatedResult.getMathData())
                .createdAt(TimeUtil.now())
                .build();

        store.getDiagnosisById().put(recordId, record);
        store.getDiagnosisOrder().add(0, recordId);

        return EvaluateResponse.builder()
                .recordId(recordId)
                .status(record.getStatus())
                .steps(record.getSteps())
                .feedback(record.getFeedback())
                .errorIndex(record.getErrorIndex())
                .mathData(record.getMathData())
                .isSocratic(record.getIsSocratic())
                .build();
    }
}
