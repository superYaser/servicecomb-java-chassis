/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.servicecomb.common.rest.codec.param;

import java.lang.reflect.Type;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Part;
import javax.ws.rs.core.Response.Status;

import org.apache.servicecomb.common.rest.RestConst;
import org.apache.servicecomb.common.rest.codec.RestClientRequest;
import org.apache.servicecomb.swagger.invocation.exception.InvocationException;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.type.TypeFactory;

import io.swagger.models.parameters.FormParameter;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.properties.FileProperty;

public class FormProcessorCreator implements ParamValueProcessorCreator {
  public static final String PARAMTYPE = "formData";

  public static class FormProcessor extends AbstractParamProcessor {
    public FormProcessor(String paramPath, JavaType targetType, Object defaultValue, boolean required) {
      super(paramPath, targetType, defaultValue, required);
    }

    @Override
    public Object getValue(HttpServletRequest request) {
      @SuppressWarnings("unchecked")
      Map<String, Object> forms = (Map<String, Object>) request.getAttribute(RestConst.FORM_PARAMETERS);
      if (forms != null && !forms.isEmpty()) {
        return convertValue(forms.get(paramPath), targetType);
      }

      if (targetType.isContainerType()) {
        Object values = request.getParameterValues(paramPath);
        //Even if the paramPath does not exist, it won't be null at now, may be optimized in the future
        if (values == null) {
          values = checkRequiredAndDefaultValue(values);
        }
        return convertValue(values, targetType);
      }

      Object value = request.getParameter(paramPath);
      if (value == null) {
        value = checkRequiredAndDefaultValue(value);
      }

      return convertValue(value, targetType);
    }

    private Object checkRequiredAndDefaultValue(Object values) {
      if (isRequired()) {
        throw new InvocationException(Status.BAD_REQUEST, "Parameter is not valid, required is true");
      }
      Object defaultValue = getDefaultValue();
      if (defaultValue != null) {
        return defaultValue;
      }
      return values;
    }

    @Override
    public void setValue(RestClientRequest clientRequest, Object arg) {
      clientRequest.addForm(paramPath, arg);
    }

    @Override
    public String getProcessorType() {
      return PARAMTYPE;
    }
  }

  public FormProcessorCreator() {
    ParamValueProcessorCreatorManager.INSTANCE.register(PARAMTYPE, this);
  }

  @Override
  public ParamValueProcessor create(Parameter parameter, Type genericParamType) {
    JavaType targetType = TypeFactory.defaultInstance().constructType(genericParamType);

    if (isPart(parameter)) {
      return new PartProcessor(parameter.getName(), targetType, ((FormParameter) parameter).getDefaultValue(),
          parameter.getRequired());
    }
    return new FormProcessor(parameter.getName(), targetType, ((FormParameter) parameter).getDefaultValue(),
        parameter.getRequired());
  }

  private boolean isPart(Parameter parameter) {
    return new FileProperty().getType().equals(((FormParameter) parameter).getType());
  }

  private static class PartProcessor extends AbstractParamProcessor {
    PartProcessor(String paramPath, JavaType targetType, Object defaultValue, boolean required) {
      super(paramPath, targetType, defaultValue, required);
    }

    @Override
    public Object getValue(HttpServletRequest request) throws Exception {
      return request.getPart(paramPath);
    }

    @Override
    public void setValue(RestClientRequest clientRequest, Object arg) throws Exception {
      clientRequest.attach(paramPath, (Part) arg);
    }

    @Override
    public String getProcessorType() {
      return PARAMTYPE;
    }
  }
}
