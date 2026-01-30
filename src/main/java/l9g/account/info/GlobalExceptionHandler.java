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
package l9g.account.info;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.ModelAndView;

/**
 *
 * @author Thorsten Ludewig <t.ludewig@gmail.com>
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler
{

  @ExceptionHandler(
    {
      org.springframework.web.client.HttpClientErrorException.BadRequest.class,
      org.springframework.web.bind.MissingServletRequestParameterException.class
    })
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ModelAndView handleBadRequestException(HttpServletRequest request,
    Exception ex)
  {
    log.debug("handleBadRequestException : {}", ex.getMessage());
    ModelAndView modelAndView = new ModelAndView("error/400");
    modelAndView.addObject("pageErrorRequestUri", request.getRequestURI());
    modelAndView.addObject("pageErrorException", ex.getMessage());
    return modelAndView;
  }

  @ExceptionHandler(
    org.springframework.web.servlet.resource.NoResourceFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ModelAndView handleNotFoundException(HttpServletRequest request,
    Exception ex)
  {
    ModelAndView modelAndView = new ModelAndView("error/404");
    modelAndView.addObject("pageErrorRequestUri", request.getRequestURI());
    return modelAndView;
  }

  @ExceptionHandler(
    {
      Exception.class, org.thymeleaf.exceptions.TemplateInputException.class
    })
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public ModelAndView handleException(HttpServletRequest request, Exception ex) 
  {
    log.debug("request={} exception={}", request, ex);

    try
    {
      log.debug("SESSION: logout!");
      request.logout();
    }
    catch(ServletException ex1)
    {
      log.warn("LOGOUT failed : {}", ex1.getMessage());
    }
    
    ModelAndView modelAndView = new ModelAndView("error/500");
    modelAndView.addObject("pageErrorRequestUri", request.getRequestURI());
    modelAndView.addObject("pageErrorException", ex.getMessage());
    modelAndView.addObject("pageErrorExceptionClassname", ex.getClass().
      getCanonicalName());

    StringBuilder stackTrace = new StringBuilder();
    for(StackTraceElement element : ex.getStackTrace())
    {
      stackTrace.append(element.toString());
      stackTrace.append('\n');
    }

    modelAndView.addObject("pageErrorStacktrace", stackTrace.toString());
    return modelAndView;
  }

  @ExceptionHandler(AccessDeniedException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  public ModelAndView handleAccessDeniedException(AccessDeniedException ex, HttpServletRequest request)
  {
    log.error("Access denied: " + ex.getMessage());
    ModelAndView modelAndView = new ModelAndView("error/403");
    modelAndView.addObject("pageErrorRequestUri", request.getRequestURI());
    modelAndView.addObject("pageErrorException", ex.getMessage());
    request.getSession().invalidate();
    return modelAndView;
  }
}
