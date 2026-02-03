/*
 * Copyright 2025 Thorsten Ludewig (t.ludewig@gmail.com).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package l9g.account.info.crypto;

import java.lang.reflect.Field;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

/**
 * A Spring {@link BeanPostProcessor} that processes fields annotated with {@link EncryptedValue}.
 * This processor automatically decrypts values from the Spring Environment that are marked
 * as encrypted, using {@link CryptoHandler}.
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EncryptedValueProcessor implements BeanPostProcessor
{
  /**
   * Spring Environment for accessing property values.
   */
  private final Environment environment;

  /**
   * Singleton instance of CryptoHandler for cryptographic operations.
   */
  private final CryptoHandler cryptoHandler = CryptoHandler.getInstance();

  /**
   * Processes beans before their initialization.
   * This method inspects fields annotated with {@link EncryptedValue},
   * decrypts their corresponding environment properties, and injects the 
   * decrypted values into the bean.
   *
   * @param bean The bean instance to process.
   * @param beanName The name of the bean.
   *
   * @return The processed bean instance.
   *
   * @throws BeansException If an error occurs during bean processing.
   */
  @Override
  public Object postProcessBeforeInitialization(Object bean, String beanName)
    throws BeansException
  {
    Field[] fields = bean.getClass().getDeclaredFields();

    for(Field field : fields)
    {
      EncryptedValue annotation = field.getAnnotation(EncryptedValue.class);
      if(annotation != null)
      {
        String environmentName = annotation.value()
          .replaceAll("^\\$\\{", "").replaceAll("}$", "");

        if(environmentName.charAt(environmentName.length() - 1) == ':')
        {
          environmentName = environmentName.substring(0, environmentName.length() - 1);
        }

        String[] nameAndDefaultValue = environmentName.split("\\:");

        String encryptedValue = (nameAndDefaultValue.length == 1)
          ? environment.getProperty(environmentName)
          : environment.getProperty(nameAndDefaultValue[0]);

        String decryptedValue = (encryptedValue != null)
          ? cryptoHandler.decrypt(encryptedValue)
          : (nameAndDefaultValue.length == 2) ? nameAndDefaultValue[1] : null;

        log.trace("{} = {}", annotation.value(), decryptedValue);

        field.setAccessible(true);
        ReflectionUtils.setField(field, bean, decryptedValue);
      }
    }
    return bean;
  }

}
