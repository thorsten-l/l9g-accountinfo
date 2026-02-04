package l9g.account.info.crypto;

import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

@Slf4j
public class EncryptedPropertiesEnvironmentPostProcessor implements
  EnvironmentPostProcessor
{

  private final CryptoHandler cryptoHandler = CryptoHandler.getInstance();

  @Override
  public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application)
  {
    log.debug("postProcessEnvironment");
    Map<String, Object> decryptedProperties = new HashMap<>();

    for(PropertySource<?> propertySource : environment.getPropertySources())
    {
      if(propertySource instanceof EnumerablePropertySource)
      {
        for(String key : ((EnumerablePropertySource<?>)propertySource).getPropertyNames())
        {
          Object value = propertySource.getProperty(key);
          if(value instanceof String)
          {
            String stringValue = (String)value;
            if(stringValue.startsWith(CryptoHandler.AES256_PREFIX))
            {
              String decryptedValue = cryptoHandler.decrypt(stringValue);
              decryptedProperties.put(key, decryptedValue);
            }
          }
        }
      }
    }

    if( ! decryptedProperties.isEmpty())
    {
      environment.getPropertySources().addFirst(
        new MapPropertySource("decryptedProperties", decryptedProperties)
      );
    }
  }

}
