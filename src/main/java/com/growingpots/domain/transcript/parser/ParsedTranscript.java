package com.growingpots.domain.transcript.parser;

import java.util.List;
import java.util.Map;

public record ParsedTranscript(
        Map<String, String> studentInfo,
        Map<String, String> graduationSummary,
        List<Map<String, String>> generalEducation,
        List<Map<String, String>> majorRequirements,
        List<Map<String, String>> courses
) {
}
