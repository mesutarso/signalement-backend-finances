package cd.gouv.finances.signalements.report.validation;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class ValidationWebConfig implements WebMvcConfigurer {
    private final ReportVisibility visibility;
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(visibility).addPathPatterns("/api/reports/**");
    }
}
