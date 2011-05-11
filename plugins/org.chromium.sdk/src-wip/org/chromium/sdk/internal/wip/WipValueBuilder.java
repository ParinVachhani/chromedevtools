// Copyright (c) 2011 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.sdk.internal.wip;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import org.chromium.sdk.JsArray;
import org.chromium.sdk.JsFunction;
import org.chromium.sdk.JsObject;
import org.chromium.sdk.JsValue;
import org.chromium.sdk.JsValue.Type;
import org.chromium.sdk.JsVariable;
import org.chromium.sdk.SyncCallback;
import org.chromium.sdk.internal.tools.v8.MethodIsBlockingException;
import org.chromium.sdk.internal.wip.WipExpressionBuilder.ObjectPropertyNameBuilder;
import org.chromium.sdk.internal.wip.WipExpressionBuilder.PropertyNameBuilder;
import org.chromium.sdk.internal.wip.WipExpressionBuilder.ValueNameBuilder;
import org.chromium.sdk.internal.wip.WipValueLoader.Getter;
import org.chromium.sdk.internal.wip.WipValueLoader.ObjectProperties;
import org.chromium.sdk.internal.wip.protocol.WipProtocol;
import org.chromium.sdk.internal.wip.protocol.input.runtime.RemoteObjectValue;
import org.chromium.sdk.util.AsyncFutureRef;

/**
 * A builder for implementations of {@link JsValue} and {@link JsVariable}.
 * It works in pair with {@link WipValueLoader}.
 */
class WipValueBuilder {
  private static final Logger LOGGER = Logger.getLogger(WipValueBuilder.class.getName());

  private final WipValueLoader valueLoader;

  WipValueBuilder(WipValueLoader valueLoader) {
    this.valueLoader = valueLoader;
  }

  public JsVariable createVariable(RemoteObjectValue valueData, ValueNameBuilder nameBuilder) {
    JsValue jsValue = wrap(valueData, nameBuilder);
    return createVariable(jsValue, nameBuilder);
  }

  public JsValue wrap(RemoteObjectValue valueData, ValueNameBuilder nameBuilder) {
    return getValueType(valueData).build(valueData, valueLoader, nameBuilder);
  }

  public static JsVariable createVariable(JsValue jsValue,
      ValueNameBuilder qualifiedNameBuilder) {
    return new VariableImpl(jsValue, qualifiedNameBuilder);
  }

  private static ValueType getValueType(RemoteObjectValue valueData) {
    RemoteObjectValue.Type protocolType = valueData.type();
    ValueType result = PROTOCOL_TYPE_TO_VALUE_TYPE.get(protocolType);

    if (result == null) {
      LOGGER.severe("Unexpected value type: " + protocolType);
      result = DEFAULT_VALUE_TYPE;
    }
    return result;
  }

  private static abstract class ValueType {
    abstract JsValue build(RemoteObjectValue valueData, WipValueLoader valueLoader,
        ValueNameBuilder nameBuilder);
  }

  private static class PrimitiveType extends ValueType {
    private final JsValue.Type jsValueType;

    PrimitiveType(JsValue.Type jsValueType) {
      this.jsValueType = jsValueType;
    }

    @Override
    JsValue build(RemoteObjectValue valueData, WipValueLoader valueLoader,
        ValueNameBuilder nameBuilder) {
      final String description = valueData.description();
      return new JsValue() {
        @Override public Type getType() {
          return jsValueType;
        }
        @Override public String getValueString() {
          return description;
        }
        @Override public JsObject asObject() {
          return null;
        }
        @Override public boolean isTruncated() {
          // TODO(peter.rybin): implement.
          return false;
        }
        @Override public void reloadHeavyValue(ReloadBiggerCallback callback,
            SyncCallback syncCallback) {
          // TODO(peter.rybin): implement.
          WipBrowserImpl.throwUnsupported();
        }
      };
    }
  }

  private static abstract class ObjectTypeBase extends ValueType {
    private final JsValue.Type jsValueType;

    ObjectTypeBase(Type jsValueType) {
      this.jsValueType = jsValueType;
    }

    @Override
    JsValue build(RemoteObjectValue valueData, WipValueLoader valueLoader,
        ValueNameBuilder nameBuilder) {
      // TODO: Implement caching here.
      return buildNewInstance(valueData, valueLoader, nameBuilder);
    }

    abstract JsValue buildNewInstance(RemoteObjectValue valueData, WipValueLoader valueLoader,
        ValueNameBuilder nameBuilder);

    abstract class JsObjectBase implements JsObject {
      private final RemoteObjectValue valueData;
      private final WipValueLoader valueLoader;
      private final ValueNameBuilder nameBuilder;
      private final AsyncFutureRef<Getter<ObjectProperties>> loadedPropertiesRef =
          new AsyncFutureRef<Getter<ObjectProperties>>();

      JsObjectBase(RemoteObjectValue valueData, WipValueLoader valueLoader,
          ValueNameBuilder nameBuilder) {
        this.valueData = valueData;
        this.valueLoader = valueLoader;
        this.nameBuilder = nameBuilder;
        if (!WipProtocol.parseHasChildren(this.valueData.hasChildren())) {
          WipValueLoader.setEmptyJsObjectProperties(loadedPropertiesRef);
        }
      }

      @Override
      public Type getType() {
        return jsValueType;
      }

      @Override
      public String getValueString() {
        return valueData.description();
      }

      @Override
      public JsObject asObject() {
        return this;
      }

      @Override
      public boolean isTruncated() {
        return false;
      }

      @Override
      public void reloadHeavyValue(ReloadBiggerCallback callback,
          SyncCallback syncCallback) {
        // TODO(peter.rybin): implement.
        WipBrowserImpl.throwUnsupported();
      }

      @Override
      public String getClassName() {
        return WipBrowserImpl.throwUnsupported();
      }

      @Override
      public Collection<? extends JsVariable> getProperties()
          throws MethodIsBlockingException {
        return getLoadedProperties().properties();
      }

      @Override
      public Collection<? extends JsVariable> getInternalProperties()
          throws MethodIsBlockingException {
        return getLoadedProperties().internalProperties();
      }

      @Override
      public JsVariable getProperty(String name) {
        return WipBrowserImpl.throwUnsupported();
      }

      @Override
      public String getRefId() {
        String objectId = valueData.objectId();
        if (objectId == null) {
          return null;
        }
        return objectId;
      }

      protected ObjectProperties getLoadedProperties() {
        if (!loadedPropertiesRef.isInitialized()) {
          doLoadProperties();
        }
        return loadedPropertiesRef.getSync().get();
      }

      private void doLoadProperties() {
        PropertyNameBuilder innerNameBuilder = new ObjectPropertyNameBuilder(nameBuilder);
        valueLoader.loadJsObjectPropertiesAsync(valueData.objectId(), innerNameBuilder,
            loadedPropertiesRef);
      }
    }
  }

  private static class ObjectType extends ObjectTypeBase {
    ObjectType() {
      super(JsValue.Type.TYPE_OBJECT);
    }

    @Override
    JsValue buildNewInstance(RemoteObjectValue valueData, WipValueLoader valueLoader,
        ValueNameBuilder nameBuilder) {
      return new ObjectTypeBase.JsObjectBase(valueData, valueLoader, nameBuilder) {
        @Override public JsArray asArray() {
          return null;
        }

        @Override public JsFunction asFunction() {
          return null;
        }
      };
    }
  }

  private static class ArrayType extends ObjectTypeBase {
    ArrayType() {
      super(JsValue.Type.TYPE_ARRAY);
    }

    @Override
    JsValue buildNewInstance(RemoteObjectValue valueData, WipValueLoader valueLoader,
        ValueNameBuilder nameBuilder) {
      return new Array(valueData, valueLoader, nameBuilder);
    }

    private class Array extends JsObjectBase implements JsArray {
      private final AtomicReference<ArrayProperties> arrayPropertiesRef =
          new AtomicReference<ArrayProperties>(null);

      Array(RemoteObjectValue valueData, WipValueLoader valueLoader,
          ValueNameBuilder nameBuilder) {
        super(valueData, valueLoader, nameBuilder);
      }

      @Override
      public JsArray asArray() {
        return this;
      }

      @Override
      public JsFunction asFunction() {
        return null;
      }

      @Override
      public int length() {
        return getArrayProperties().getLength();
      }

      @Override
      public JsVariable get(int index) throws MethodIsBlockingException {
        return getArrayProperties().getSparseArrayMap().get(index);
      }

      @Override
      public SortedMap<Integer, ? extends JsVariable> toSparseArray()
          throws MethodIsBlockingException {
        return getArrayProperties().getSparseArrayMap();
      }

      private ArrayProperties getArrayProperties() {
        ArrayProperties result = arrayPropertiesRef.get();
        if (result == null) {
          ArrayProperties arrayProperties = buildArrayProperties();
          // Only set if concurrent thread hasn't set its version
          arrayPropertiesRef.compareAndSet(null, arrayProperties);
          return arrayPropertiesRef.get();
        } else {
          return result;
        }
      }

      private ArrayProperties buildArrayProperties() {
        ObjectProperties loadedProperties = getLoadedProperties();
        final TreeMap<Integer, JsVariable> map = new TreeMap<Integer, JsVariable>();
        JsValue lengthValue = null;
        for (JsVariable variable : loadedProperties.properties()) {
          String name = variable.getName();
          if (WipExpressionBuilder.ALL_DIGITS.matcher(name).matches()) {
            Integer number = Integer.valueOf(name);
            map.put(number, variable);
          } else if ("length".equals(name)) {
            lengthValue = variable.getValue();
          }
        }
        int length;
        try {
          length = Integer.parseInt(lengthValue.getValueString());
        } catch (NumberFormatException e) {
          length = -1;
        }
        return new ArrayProperties(length, map);
      }
    }

    private static class ArrayProperties {
      final int length;
      final SortedMap<Integer, ? extends JsVariable> sparseArrayMap;

      ArrayProperties(int length,
          SortedMap<Integer, ? extends JsVariable> sparseArrayMap) {
        this.length = length;
        this.sparseArrayMap = sparseArrayMap;
      }
      public int getLength() {
        return length;
      }

      public SortedMap<Integer, ? extends JsVariable> getSparseArrayMap() {
        return sparseArrayMap;
      }
    }
  }

  private static class FunctionType extends ObjectTypeBase {
    FunctionType() {
      super(JsValue.Type.TYPE_FUNCTION);
    }

    @Override
    JsValue buildNewInstance(RemoteObjectValue valueData, WipValueLoader valueLoader,
        ValueNameBuilder nameBuilder) {
      return new ObjectTypeBase.JsObjectBase(valueData, valueLoader, nameBuilder) {
        @Override public JsArray asArray() {
          return null;
        }

        @Override public JsFunction asFunction() {
          // TODO: make it a function, when backend provides data!
          return null;
        }
      };
    }
  }

  private static class VariableImpl implements JsVariable {
    private final JsValue jsValue;
    private final ValueNameBuilder qualifiedNameBuilder;
    private volatile String qualifiedName = null;

    public VariableImpl(JsValue jsValue, ValueNameBuilder qualifiedNameBuilder) {
      this.jsValue = jsValue;
      this.qualifiedNameBuilder = qualifiedNameBuilder;
    }

    @Override
    public boolean isReadable() {
      return true;
    }

    @Override
    public JsValue getValue() {
      return jsValue;
    }

    @Override
    public String getName() {
      return qualifiedNameBuilder.getShortName();
    }

    @Override
    public boolean isMutable() {
      return false;
    }

    @Override
    public void setValue(String newValue, SetValueCallback callback)
        throws UnsupportedOperationException {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getFullyQualifiedName() {
      String result = qualifiedName;
      if (result == null) {
        StringBuilder builder = new StringBuilder();
        qualifiedNameBuilder.append(builder);
        result = builder.toString();
        qualifiedName = result;
      }
      return result;
    }
  }

  private static final Map<RemoteObjectValue.Type, ValueType> PROTOCOL_TYPE_TO_VALUE_TYPE;
  private static final ValueType DEFAULT_VALUE_TYPE;
  static {
    PROTOCOL_TYPE_TO_VALUE_TYPE = new HashMap<RemoteObjectValue.Type, ValueType>();
    PROTOCOL_TYPE_TO_VALUE_TYPE.put(RemoteObjectValue.Type.STRING,
        new PrimitiveType(JsValue.Type.TYPE_STRING));
    PROTOCOL_TYPE_TO_VALUE_TYPE.put(RemoteObjectValue.Type.BOOLEAN,
        new PrimitiveType(JsValue.Type.TYPE_BOOLEAN));
    PROTOCOL_TYPE_TO_VALUE_TYPE.put(RemoteObjectValue.Type.NUMBER,
        new PrimitiveType(JsValue.Type.TYPE_NUMBER));
    PROTOCOL_TYPE_TO_VALUE_TYPE.put(RemoteObjectValue.Type.NULL,
        new PrimitiveType(JsValue.Type.TYPE_NULL));
    PROTOCOL_TYPE_TO_VALUE_TYPE.put(RemoteObjectValue.Type.UNDEFINED,
        new PrimitiveType(JsValue.Type.TYPE_UNDEFINED));

    ObjectType objectType = new ObjectType();
    PROTOCOL_TYPE_TO_VALUE_TYPE.put(RemoteObjectValue.Type.OBJECT, objectType);
    PROTOCOL_TYPE_TO_VALUE_TYPE.put(RemoteObjectValue.Type.ARRAY, new ArrayType());
    PROTOCOL_TYPE_TO_VALUE_TYPE.put(RemoteObjectValue.Type.FUNCTION, new FunctionType());

    DEFAULT_VALUE_TYPE = objectType;
  }
}
