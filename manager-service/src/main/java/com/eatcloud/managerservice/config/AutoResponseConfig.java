package com.eatcloud.managerservice.config;

import com.eatcloud.autoresponse.annotation.EnableAutoResponse;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableAutoResponse //  AutoResponseConfiguration를 @Import 하며, ExceptionHandler를 컴포넌트 스캔 등록
public class AutoResponseConfig {

}
