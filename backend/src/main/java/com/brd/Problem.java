package com.brd;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.dao.DataIntegrityViolationException;

public class Problem extends RuntimeException {
    final int status; final String code;
    Problem(int status,String code,String message){super(message);this.status=status;this.code=code;}
}

@RestControllerAdvice
class ProblemHandler {
    @ExceptionHandler(Problem.class)
    ResponseEntity<?> problem(Problem p){return ResponseEntity.status(p.status).body(Map.of("code",p.code,"message",p.getMessage()));}
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<?> large(){return ResponseEntity.status(413).body(Map.of("code","FILE_TOO_LARGE","message","文件不得超过 20 MB"));}
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> conflict(){return ResponseEntity.status(409).body(Map.of("code","CONFLICT","message","记录已存在或操作发生冲突，请刷新"));}
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<?> input(){return ResponseEntity.badRequest().body(Map.of("code","INVALID_INPUT","message","请求参数格式错误"));}
}
