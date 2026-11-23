package com.growingpots.domain.transcript.parser;

// 페이지·글자·연산 수나 압축 해제 크기 상한(#290)에 걸린 경우. 정상 졸업사정표는 걸리지 않는 값이라
// 악의적인 파일일 가능성이 있어 일반 파싱 실패와 구분해 알림을 보낸다(#292).
public class PdfLimitExceededException extends PdfParsingException {

    public PdfLimitExceededException(String message) {
        super(message);
    }
}
