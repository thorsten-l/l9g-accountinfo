/*
 * Copyright 2024 Thorsten Ludewig <t.ludewig@gmail.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package l9g.account.info.config;

import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

/**
 *
 * @author Thorsten Ludewig <t.ludewig@gmail.com>
 */
@Configuration
@Slf4j
public class LocaleWebConfig implements WebMvcConfigurer
{
  @Value("${app.default.locale}")
  private String defaultLocale;
  
  @Value("${spring.security.oauth2.client.provider.app.issuer-uri}")
  private String issuerUri;

  @Bean
  public LocaleResolver localeResolver()
  {
    Locale locale = Locale.of(defaultLocale);
    log.debug("set default locale to {}", locale);
    CookieLocaleResolver resolver = new CookieLocaleResolver();
    resolver.setDefaultLocale(locale);
    return resolver;
  }

  @Bean
  public LocaleChangeInterceptor localeChangeInterceptor()
  {
    log.debug("localeChangeInterceptor");
    LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
    interceptor.setParamName("lang");
    return interceptor;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry)
  {
    log.debug("addInterceptors");
    log.debug("issuer uri = {}", issuerUri);
    registry.addInterceptor(localeChangeInterceptor());
  }
}
