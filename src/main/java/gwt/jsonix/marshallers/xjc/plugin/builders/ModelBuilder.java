/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package gwt.jsonix.marshallers.xjc.plugin.builders;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.namespace.QName;

import com.sun.codemodel.ClassType;
import com.sun.codemodel.JBlock;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JClassAlreadyExistsException;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JCommentPart;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JDocComment;
import com.sun.codemodel.JEnumConstant;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JVar;
import com.sun.tools.xjc.model.CClassInfo;
import com.sun.tools.xjc.model.CClassInfoParent;
import com.sun.tools.xjc.model.CElement;
import com.sun.tools.xjc.model.CElementInfo;
import com.sun.tools.xjc.model.CElementPropertyInfo;
import com.sun.tools.xjc.model.CEnumLeafInfo;
import com.sun.tools.xjc.model.CPluginCustomization;
import com.sun.tools.xjc.model.CPropertyInfo;
import com.sun.tools.xjc.model.CReferencePropertyInfo;
import com.sun.tools.xjc.model.Model;
import com.sun.tools.xjc.model.nav.NClass;
import gwt.jsonix.marshallers.xjc.plugin.dtos.ConstructorMapper;
import gwt.jsonix.marshallers.xjc.plugin.exceptions.ParseModelException;
import jsinterop.annotations.JsOverlay;
import jsinterop.annotations.JsPackage;
import jsinterop.annotations.JsType;
import jsinterop.base.JsArrayLike;
import org.apache.commons.lang3.StringUtils;
import org.hisrc.jsonix.settings.LogLevelSetting;
import org.jvnet.jaxb2_commons.plugin.inheritance.Customizations;
import org.jvnet.jaxb2_commons.plugin.inheritance.ExtendsClass;
import org.jvnet.jaxb2_commons.plugin.inheritance.ExtendsClassReader;
import org.jvnet.jaxb2_commons.plugin.inheritance.util.JavaTypeParser;
import org.jvnet.jaxb2_commons.util.CustomizationUtils;

import static gwt.jsonix.marshallers.xjc.plugin.utils.BuilderUtils.addAddAllMethodForArray;
import static gwt.jsonix.marshallers.xjc.plugin.utils.BuilderUtils.addAddAllMethodForJsArrayLike;
import static gwt.jsonix.marshallers.xjc.plugin.utils.BuilderUtils.addAddMethodForArray;
import static gwt.jsonix.marshallers.xjc.plugin.utils.BuilderUtils.addAddMethodForJsArrayLike;
import static gwt.jsonix.marshallers.xjc.plugin.utils.BuilderUtils.addListGetterForArray;
import static gwt.jsonix.marshallers.xjc.plugin.utils.BuilderUtils.addListGetterForJsArrayLike;
import static gwt.jsonix.marshallers.xjc.plugin.utils.BuilderUtils.addListSetterForArray;
import static gwt.jsonix.marshallers.xjc.plugin.utils.BuilderUtils.addListSetterForJsArrayLike;
import static gwt.jsonix.marshallers.xjc.plugin.utils.BuilderUtils.addNativeGetter;
import static gwt.jsonix.marshallers.xjc.plugin.utils.BuilderUtils.addNativeSetter;
import static gwt.jsonix.marshallers.xjc.plugin.utils.BuilderUtils.addRemoveMethodForArray;
import static gwt.jsonix.marshallers.xjc.plugin.utils.BuilderUtils.addRemoveMethodForJsArrayLike;
import static gwt.jsonix.marshallers.xjc.plugin.utils.BuilderUtils.getJavaRef;
import static gwt.jsonix.marshallers.xjc.plugin.utils.BuilderUtils.log;
import static gwt.jsonix.marshallers.xjc.plugin.utils.ClassNameUtils.getJsInteropTypeName;
import static org.jvnet.jaxb2_commons.plugin.inheritance.Customizations.EXTENDS_ELEMENT_NAME;

/**
 * Actual builder for <b>JSInterop</b> models
 */
public class ModelBuilder {

    private ModelBuilder() {
    }

    /**
     * Method to create the <b>JSInterop</b> representation oif <b>xsd</b> definitions
     * @param definedClassesMap
     * @param model
     * @param jCodeModel
     * @param packageModuleMap
     * @param jsUtilsClass
     * @param mapToPopulate the <code>Map&lt;String, List&lt;ConstructorMapper&gt;&gt;</code> to be used inside <code>MainJsBuilder</code> to write instantiation of js constructors
     * @throws Exception
     */
    public static void generateJSInteropModels(Map<String, JClass> definedClassesMap, Model model, JCodeModel jCodeModel, Map<String, String> packageModuleMap, JDefinedClass jsUtilsClass, JDefinedClass jsiNameClass, Map<String, List<ConstructorMapper>> mapToPopulate) throws ParseModelException, JClassAlreadyExistsException {
        definedClassesMap.clear();
        log(LogLevelSetting.DEBUG, "Generating JSInterop code...");
        for (CClassInfo cClassInfo : model.beans().values()) {
            populateJCodeModel(definedClassesMap, jCodeModel, cClassInfo, packageModuleMap, model, jsUtilsClass, jsiNameClass, mapToPopulate);
        }
    }

    protected static void populateJCodeModel(Map<String, JClass> definedClassesMap, JCodeModel toPopulate, CClassInfo cClassInfo, Map<String, String> packageModuleMap, Model model, JDefinedClass jsUtilsClass, JDefinedClass jsiNameClass, Map<String, List<ConstructorMapper>> mapToPopulate) throws JClassAlreadyExistsException, ParseModelException {
        log(LogLevelSetting.DEBUG, "Generating  JCode model...");
        if (definedClassesMap.containsKey(cClassInfo.fullName())) {
            return;
        }
        final CClassInfoParent parent = cClassInfo.parent();
        final JDefinedClass jDefinedClass;
        final JExpression nameSpaceExpression;
        final CClassInfo basecClassInfo = cClassInfo.getBaseClass();
        JClass jDefinedBaseClass = null;

        String shortClassName = cClassInfo.shortName;
        String nameSpace = shortClassName;

        // Read extends customisation from JAXB Basics Inheritance plugin binding.
        // Explicit values found by JAXB bindings are overwritten by inheritance defined in the XSD being processed.
        final CPluginCustomization extendsClassCustomization = CustomizationUtils.findCustomization(cClassInfo, EXTENDS_ELEMENT_NAME);
        if (Objects.nonNull(extendsClassCustomization)) {
            jDefinedBaseClass = getFromExtendsClassCustomization(definedClassesMap, toPopulate, extendsClassCustomization);
        }
        if (basecClassInfo != null) { // This is the "extended" class
            jDefinedBaseClass = getFromBasecClassInfo(definedClassesMap, toPopulate, packageModuleMap, model, basecClassInfo, jsUtilsClass, jsiNameClass, mapToPopulate);
        }
        boolean hasClassParent = (parent != null && !(parent instanceof CClassInfoParent.Package));

        final String jsTypeName;
        final String parentClassName;
        final String moduleName;
        String nameSpaceString = null;
        if (hasClassParent && definedClassesMap.containsKey(parent.fullName())) { // This is for inner classes
            JDefinedClass parentJSIClass = getParentJSIClass(definedClassesMap, parent.fullName());
            jDefinedClass = getFromParent(jDefinedBaseClass, parentJSIClass, nameSpace);
            moduleName = packageModuleMap.get(jDefinedClass._package().name());
            nameSpaceString = getJsInteropTypeName(moduleName, parentJSIClass.fullName());
            nameSpaceExpression = JExpr.lit(nameSpaceString);
            jsTypeName = shortClassName;
            parentClassName = ((CClassInfo) cClassInfo.parent()).shortName;
        } else {
            final String fullClassName = cClassInfo.getOwnerPackage().name() + ".JSI" + nameSpace;

            jDefinedClass = jDefinedBaseClass != null ? toPopulate._class(fullClassName)._extends(jDefinedBaseClass) : toPopulate._class(fullClassName);
            moduleName = packageModuleMap.get(jDefinedClass._package().name());
            nameSpaceExpression = toPopulate.ref(JsPackage.class).staticRef("GLOBAL");
            jsTypeName = getJsInteropTypeName(moduleName, jDefinedClass.fullName());
            parentClassName = null;
        }

        definedClassesMap.put(cClassInfo.fullName(), jDefinedClass);
        JDocComment comment = jDefinedClass.javadoc();
        String commentString = "JSInterop adapter for <code>" + nameSpace + "</code>";
        comment.append(commentString);

        jDefinedClass.annotate(toPopulate.ref(JsType.class))
                .param("namespace", nameSpaceExpression)
                .param("name", "Object")
                .param("isNative", true);
        String typeNameConstant = Stream.of(moduleName, parentClassName, nameSpace).filter(Objects::nonNull).collect(Collectors.joining("."));

        ConstructorMapper toAdd = new ConstructorMapper(typeNameConstant, jsTypeName, nameSpaceString);
        mapToPopulate.computeIfAbsent(moduleName, k -> new ArrayList<>());
        mapToPopulate.get(moduleName).add(toAdd);

        final JFieldVar typeNameField = addTypeName(jDefinedClass, toPopulate, typeNameConstant);
        addInstanceOf(jDefinedClass, jsUtilsClass, typeNameField);

        if (cClassInfo.getTypeName() != null) {
            addGetJSINameMethod(jDefinedClass, cClassInfo.getTypeName(), jsiNameClass);
        }
        addGetTypeNameProperty(toPopulate, jDefinedClass);
        for (CPropertyInfo cPropertyInfo : cClassInfo.getProperties()) {
            addProperty(toPopulate, jDefinedClass, cPropertyInfo, definedClassesMap, packageModuleMap, model, jsUtilsClass, jsiNameClass, mapToPopulate);
        }
        if (cClassInfo.declaresAttributeWildcard()) {
            addOtherAttributesProperty(toPopulate, jDefinedClass, jsUtilsClass, nameSpace);
        }
    }

    protected static void populateJCodeModel(Map<String, JClass> definedClassesMap, JCodeModel toPopulate, CEnumLeafInfo cEnumLeafInfo) throws JClassAlreadyExistsException {
        log(LogLevelSetting.DEBUG, "Generating  JCode model...");
        String fullClassName = cEnumLeafInfo.parent.getOwnerPackage().name() + ".JSI" + cEnumLeafInfo.shortName;
        final JDefinedClass jDefinedClass = toPopulate._class(fullClassName, ClassType.ENUM);
        jDefinedClass.annotate(toPopulate.ref(JsType.class))
                .param("name", cEnumLeafInfo.shortName);
        definedClassesMap.put(cEnumLeafInfo.fullName(), jDefinedClass);
        JDocComment comment = jDefinedClass.javadoc();
        String commentString = "JSInterop adapter for <code>" + cEnumLeafInfo.shortName + "</code>";
        comment.append(commentString);
        cEnumLeafInfo.getConstants().forEach(cEnumConstant -> {
            final JEnumConstant jEnumConstant = jDefinedClass.enumConstant(cEnumConstant.getName());
            if (cEnumLeafInfo.needsValueField()) {
                jEnumConstant.arg(JExpr.lit(cEnumConstant.getLexicalValue()));
            }
        });
        if (cEnumLeafInfo.needsValueField()) {
            addEnumValueField(toPopulate, jDefinedClass);
        }
    }

    protected static JClass getFromExtendsClassCustomization(Map<String, JClass> definedClassesMap, JCodeModel toPopulate, CPluginCustomization extendsClassCustomization) {
        final ExtendsClass extendsClass = (ExtendsClass) CustomizationUtils.unmarshall(Customizations.getContext(), extendsClassCustomization);
        final String extendsClassName = ExtendsClassReader.getValue(extendsClass);
        return parseClass(extendsClassName, toPopulate, definedClassesMap);
    }

    protected static JClass getFromBasecClassInfo(Map<String, JClass> definedClassesMap, JCodeModel toPopulate, Map<String, String> packageModuleMap, Model model, CClassInfo basecClassInfo, JDefinedClass jsUtilsClass, JDefinedClass jsiNameClass, Map<String, List<ConstructorMapper>> mapToPopulate) throws ParseModelException, JClassAlreadyExistsException {
        if (!definedClassesMap.containsKey(basecClassInfo.fullName())) {
            populateJCodeModel(definedClassesMap, toPopulate, basecClassInfo, packageModuleMap, model, jsUtilsClass, jsiNameClass, mapToPopulate);
        }
        return definedClassesMap.get(basecClassInfo.fullName());
    }

    protected static JDefinedClass getParentJSIClass(Map<String, JClass> definedClassesMap, String parentFullName) {
        return (JDefinedClass) definedClassesMap.get(parentFullName);
    }

    protected static JDefinedClass getFromParent(JClass jDefinedBaseClass, JDefinedClass parentJSIClass, String nameSpace) throws JClassAlreadyExistsException {
        int mod = JMod.PUBLIC + JMod.STATIC;
        return jDefinedBaseClass != null ? parentJSIClass._class(mod, "JSI" + nameSpace)._extends(jDefinedBaseClass) : parentJSIClass._class(mod, "JSI" + nameSpace);
    }

    protected static void addEnumValueField(JCodeModel toPopulate, JDefinedClass jDefinedClass) {
        final JClass propertyRef = toPopulate.ref(String.class);
        String privatePropertyName = "value";
        int mod = JMod.PRIVATE + JMod.FINAL;
        final JFieldVar field = jDefinedClass.field(mod, propertyRef, privatePropertyName);
        mod = JMod.NONE;
        final JMethod constructor = jDefinedClass.constructor(mod);
        final JVar param = constructor.param(propertyRef, privatePropertyName);
        constructor.body().assign(JExpr._this().ref(field), param);
        mod = JMod.PUBLIC;
        JMethod getterMethod = jDefinedClass.method(mod, propertyRef, privatePropertyName);
        getterMethod.body()._return(field);
    }

    protected static void addInstanceOf(final JDefinedClass jDefinedClass,
                                        final JDefinedClass jsUtilsClass,
                                        final JFieldVar typeNameField) {

        final int mods = JMod.PUBLIC + JMod.STATIC;
        final String methodName = "instanceOf";

        final JMethod instanceOfMethod = jDefinedClass.method(mods, boolean.class, methodName);
        final JBlock block = instanceOfMethod.body();
        final JVar typeParam = instanceOfMethod.param(JMod.FINAL, Object.class, "instance");
        final JInvocation getTypeName = jsUtilsClass.staticInvoke("getTypeName").arg(typeParam);

        instanceOfMethod.annotate(JsOverlay.class);

        block._return(typeNameField.invoke("equals").arg(getTypeName));
    }

    protected static JFieldVar addTypeName(final JDefinedClass jDefinedClass,
                                           final JCodeModel jCodeModel,
                                           final String typeName) {

        final JClass propertyRef = jCodeModel.ref(String.class);
        final int mods = JMod.PUBLIC + JMod.STATIC + JMod.FINAL;
        final JFieldVar typeNameField = jDefinedClass.field(mods, propertyRef, "TYPE");

        typeNameField.annotate(JsOverlay.class);
        typeNameField.init(JExpr.lit(typeName));

        return typeNameField;
    }

    protected static void addGetJSINameMethod(JDefinedClass jDefinedClass, QName typeName, JDefinedClass jsiNameClass) {
        log(LogLevelSetting.DEBUG, String.format("Add getJSIName method to object %1$s.%2$s ...", jDefinedClass._package().name(), jDefinedClass.name()));
        String getterMethodName = "getJSIName";
        int mod = JMod.PUBLIC + JMod.STATIC;
        JMethod getterMethod = jDefinedClass.method(mod, jsiNameClass, getterMethodName);
        getterMethod.annotate(JsOverlay.class);
        JDocComment getterComment = getterMethod.javadoc();
        String commentString = "Getter for specific <code>JSIName</code>";
        getterComment.append(commentString);
        JCommentPart getterCommentReturnPart = getterComment.addReturn();
        getterCommentReturnPart.add(commentString);
        final JBlock body = getterMethod.body();
        final JVar toReturn = body.decl(jsiNameClass, "toReturn", JExpr._new(jsiNameClass));
        body.add(toReturn.invoke("setNamespaceURI").arg(typeName.getNamespaceURI()));
        body.add(toReturn.invoke("setLocalPart").arg(typeName.getLocalPart()));
        body.add(toReturn.invoke("setPrefix").arg(typeName.getPrefix()));
        body.add(toReturn.invoke("setKey").arg("{" + typeName.getNamespaceURI() + "}"));
        if (!StringUtils.isEmpty(typeName.getPrefix())) {
            body.add(toReturn.invoke("setString").arg("{" + typeName.getNamespaceURI() + "}" + typeName.getPrefix() + ":" + typeName.getLocalPart()));
        } else {
            body.add(toReturn.invoke("setString").arg("{" + typeName.getNamespaceURI() + "}" + typeName.getLocalPart()));
        }
        body._return(toReturn);
    }

    protected static void addGetTypeNameProperty(JCodeModel jCodeModel, JDefinedClass jDefinedClass) {
        log(LogLevelSetting.DEBUG, String.format("Add getTYPENAME property to object %1$s.%2$s ...", jDefinedClass._package().name(), jDefinedClass.name()));
        JClass parameterRef = jCodeModel.ref(String.class);
        addNativeGetter(jCodeModel, jDefinedClass, parameterRef, "TYPE_NAME", "TYPE_NAME");
    }

    protected static void addProperty(JCodeModel jCodeModel, JDefinedClass jDefinedClass, CPropertyInfo cPropertyInfo, Map<String, JClass> definedClassesMap, Map<String, String> packageModuleMap, Model model, JDefinedClass jsUtilsClass, JDefinedClass jsiNameClass, Map<String, List<ConstructorMapper>> mapToPopulate) throws ParseModelException, JClassAlreadyExistsException {

        final JClass propertyRef = getPropertyRef(jCodeModel, cPropertyInfo, jDefinedClass.fullName(), definedClassesMap, packageModuleMap, model, jsUtilsClass, jsiNameClass, mapToPopulate);
        final String publicPropertyName = cPropertyInfo.getName(true);
        final String privatePropertyName = cPropertyInfo.getName(false);

        addGetter(jCodeModel, jDefinedClass, jsUtilsClass, propertyRef, publicPropertyName, privatePropertyName);
        addSetter(jCodeModel, jDefinedClass, propertyRef, publicPropertyName, privatePropertyName, jsUtilsClass);
    }

    /**
     * Generates an attribute wildcard property on a class.
     */
    protected static void addOtherAttributesProperty(JCodeModel jCodeModel, JDefinedClass jDefinedClass, JDefinedClass jsUtilsClass, String nameSpace) {

        log(LogLevelSetting.DEBUG, String.format("Add getOtherAttributes property to object %1$s.%2$s ...", jDefinedClass._package().name(), jDefinedClass.name()));

        final JClass parameterRef = jCodeModel.ref(Map.class).narrow(QName.class, String.class);
        final JMethod otherAttributesGetter = addNativeGetter(jCodeModel, jDefinedClass, parameterRef, "OtherAttributes", "otherAttributes");

        addSetter(jCodeModel, jDefinedClass, parameterRef, "OtherAttributes", "otherAttributes", jsUtilsClass);
        addStaticOtherAttributesGetter(jCodeModel, jDefinedClass, otherAttributesGetter, jsUtilsClass);
    }

    protected static void addStaticOtherAttributesGetter(final JCodeModel jCodeModel,
                                                         final JDefinedClass jDefinedClass,
                                                         final JMethod otherAttributesGetter,
                                                         final JDefinedClass jsUtilsClass) {

        log(LogLevelSetting.DEBUG, String.format("Add getOtherAttributesMap method to object %1$s.%2$s ...", jDefinedClass._package().name(), jDefinedClass.name()));

        final int mods = JMod.PUBLIC + JMod.STATIC;
        final JClass parameterRef = jCodeModel.ref(Map.class).narrow(QName.class, String.class);

        final JMethod jMethod = jDefinedClass.method(mods, parameterRef, "getOtherAttributesMap");
        final JVar instanceParam = jMethod.param(JMod.FINAL, jDefinedClass, "instance");
        final JBlock block = jMethod.body();
        final JInvocation instanceOtherAttributes = instanceParam.invoke(otherAttributesGetter);

        jMethod.annotate(JsOverlay.class);

        block._return(jsUtilsClass.staticInvoke("toAttributesMap").arg(instanceOtherAttributes));
    }

    protected static JClass getPropertyRef(JCodeModel jCodeModel, CPropertyInfo cPropertyInfo, String outerClass, Map<String, JClass> definedClassesMap, Map<String, String> packageModuleMap, Model model, JDefinedClass jsUtilsClass, JDefinedClass jsiNameClass, Map<String, List<ConstructorMapper>> mapToPopulate) throws ParseModelException, JClassAlreadyExistsException {
        JClass typeRef = getOrCreatePropertyRef(cPropertyInfo, outerClass, definedClassesMap, jCodeModel, packageModuleMap, model, jsUtilsClass, jsiNameClass, mapToPopulate);
        if (typeRef == null) {
            log(LogLevelSetting.WARN, "Failed to retrieve JClass for " + cPropertyInfo.getName(false) + " inside the JCodeModel");
            return null;
        }
        log(LogLevelSetting.DEBUG, typeRef.toString());
        if (cPropertyInfo.isCollection()) {
            if (typeRef.unboxify().isPrimitive()) {
                return typeRef.unboxify().array();
            } else {
                JClass rawArrayListClass = jCodeModel.ref(JsArrayLike.class);
                return rawArrayListClass.narrow(typeRef);
            }
        } else {
            if (!typeRef.isPrimitive()) {
                typeRef = jCodeModel.ref(typeRef.unboxify().fullName());
            }
            return typeRef;
        }
    }

    protected static JClass getOrCreatePropertyRef(CPropertyInfo cPropertyInfo, String outerClass, Map<String, JClass> definedClassesMap, JCodeModel jCodeModel, Map<String, String> packageModuleMap, Model model, JDefinedClass jsUtilsClass, JDefinedClass jsiNameClass, Map<String, List<ConstructorMapper>> mapToPopulate) throws ParseModelException, JClassAlreadyExistsException {
        String originalClassName = getOriginalClassName(cPropertyInfo, outerClass);
        return getOrCreatePropertyRef(originalClassName, definedClassesMap, jCodeModel, packageModuleMap, model, jsUtilsClass, !cPropertyInfo.isCollection(), jsiNameClass, mapToPopulate);
    }

    protected static JClass getOrCreatePropertyRef(String originalClassName, Map<String, JClass> definedClassesMap, JCodeModel jCodeModel, Map<String, String> packageModuleMap, Model model, JDefinedClass jsUtilsClass, boolean toUnbox, JDefinedClass jsiNameClass, Map<String, List<ConstructorMapper>> mapToPopulate) throws ParseModelException, JClassAlreadyExistsException {
        JClass toReturn;
        final Optional<JClass> javaRef = getJavaRef(originalClassName, jCodeModel, toUnbox);
        if (javaRef.isPresent()) {
            toReturn = javaRef.get();
        } else {
            if (!definedClassesMap.containsKey(originalClassName)) {
                Optional<NClass> nClassKey = model.beans().keySet().stream().filter(nClass -> nClass.fullName().equals(originalClassName)).findFirst();
                Optional<NClass> nEnumKey = model.enums().keySet().stream().filter(nClass -> nClass.fullName().equals(originalClassName)).findFirst();
                if (nClassKey.isPresent()) {
                    populateJCodeModel(definedClassesMap, jCodeModel, model.beans().get(nClassKey.get()), packageModuleMap, model, jsUtilsClass, jsiNameClass, mapToPopulate);
                } else if (nEnumKey.isPresent()) {
                    populateJCodeModel(definedClassesMap, jCodeModel, model.enums().get(nEnumKey.get()));
                } else {
                    throw new ParseModelException("Failed to retrieve " + originalClassName + " inside the Model");
                }
            }
            toReturn = definedClassesMap.get(originalClassName);
        }
        return toReturn;
    }

    protected static String getOriginalClassName(CPropertyInfo cPropertyInfo, String outerClass) {
        String fullClassName = null;
        log(LogLevelSetting.DEBUG, "getClassName...");
        if (cPropertyInfo instanceof CReferencePropertyInfo) {
            final CReferencePropertyInfo cReferencePropertyInfo = (CReferencePropertyInfo) cPropertyInfo;
            final Set<CElement> elements = (cReferencePropertyInfo).getElements();
            if (!elements.isEmpty()) {
                final CElementInfo cElement = (CElementInfo) elements.toArray()[0];
                CElementPropertyInfo property = cElement.getProperty();
                fullClassName = getPropertyClassName(property);
            } else if (cReferencePropertyInfo.baseType != null) {
                fullClassName = cReferencePropertyInfo.baseType.fullName();
            }
        } else {
            fullClassName = getPropertyClassName(cPropertyInfo);
        }
        if (fullClassName == null) {
            log(LogLevelSetting.WARN, "Failed to log ref for " + cPropertyInfo.getName(false) + " that is a " + cPropertyInfo.getClass().getCanonicalName() + " defined inside " + outerClass, null);
            fullClassName = "java.lang.Object";
        }
        if (fullClassName.equals("javax.xml.datatype.XMLGregorianCalendar")) {
            fullClassName = "java.util.Date";
        }
        return fullClassName;
    }

    protected static String getPropertyClassName(CPropertyInfo toLog) {
        String toReturn = null;
        if (!toLog.ref().isEmpty()) {
            toReturn = toLog.ref().iterator().next().getType().fullName();
            log(LogLevelSetting.DEBUG, "cPropertyInfo.ref().iterator().next().getType(): " + toReturn);
        }
        return toReturn;
    }

    protected static void addGetter(final JCodeModel jCodeModel,
                                    final JDefinedClass jDefinedClass,
                                    final JDefinedClass jsUtilsClass,
                                    final JClass propertyRef,
                                    final String publicPropertyName,
                                    final String privatePropertyName) {

        final boolean isJsArrayLike = propertyRef != null && Objects.equals(propertyRef.erasure().name(), "JsArrayLike");
        final boolean isArray = propertyRef != null && propertyRef.isArray();

        if (isJsArrayLike || isArray) {
            final String nativePropertyName = "Native" + publicPropertyName;
            if (isArray) {
                addListGetterForArray(jCodeModel, jDefinedClass, propertyRef.elementType(), publicPropertyName, privatePropertyName);
                addAddMethodForArray(jCodeModel, jDefinedClass, propertyRef.elementType(), publicPropertyName, privatePropertyName);
                addAddAllMethodForArray(jCodeModel, jDefinedClass, propertyRef.elementType(), publicPropertyName, privatePropertyName);
                addRemoveMethodForArray(jCodeModel, jDefinedClass, propertyRef.elementType(), publicPropertyName, privatePropertyName);
            } else {
                JClass propertyRefTypeParam = propertyRef.getTypeParameters().get(0);
                addListGetterForJsArrayLike(jCodeModel, jDefinedClass, jsUtilsClass, propertyRefTypeParam, publicPropertyName, privatePropertyName);
                addAddMethodForJsArrayLike(jCodeModel, jDefinedClass, jsUtilsClass, propertyRefTypeParam, publicPropertyName, privatePropertyName);
                addAddAllMethodForJsArrayLike(jCodeModel, jDefinedClass, jsUtilsClass, propertyRefTypeParam, publicPropertyName, privatePropertyName);
                addRemoveMethodForJsArrayLike(jCodeModel, jDefinedClass, jsUtilsClass, publicPropertyName, privatePropertyName);
            }
            addNativeGetter(jCodeModel, jDefinedClass, propertyRef, nativePropertyName, privatePropertyName);
        } else {
            addNativeGetter(jCodeModel, jDefinedClass, propertyRef, publicPropertyName, privatePropertyName);
        }
    }

    protected static void addSetter(JCodeModel jCodeModel, JDefinedClass jDefinedClass, JClass propertyRef, String
            publicPropertyName, String privatePropertyName, JDefinedClass jsUtilsClass) {
        final boolean isJsArrayLike = propertyRef != null && Objects.equals(propertyRef.erasure().name(), "JsArrayLike");
        final boolean isArray = propertyRef != null && propertyRef.isArray();

        if (isJsArrayLike || isArray) {
            final String nativePropertyName = "Native" + publicPropertyName;
            if (isArray) {
                addListSetterForArray(jCodeModel, jDefinedClass, propertyRef.elementType(), publicPropertyName, privatePropertyName);
            } else {
                final JClass propertyRefTypeParam = propertyRef.getTypeParameters().get(0);
                addListSetterForJsArrayLike(jCodeModel, jDefinedClass, jsUtilsClass, propertyRefTypeParam, publicPropertyName, privatePropertyName);
            }
            addNativeSetter(jCodeModel, jDefinedClass, propertyRef, nativePropertyName, privatePropertyName);
        } else {
            addNativeSetter(jCodeModel, jDefinedClass, propertyRef, publicPropertyName, privatePropertyName);
        }
    }

    protected static JClass parseClass(String className,
                                       JCodeModel codeModel,
                                       Map<String, JClass> definedClassesMap) {
        return new JavaTypeParser(definedClassesMap).parseClass(className, codeModel);
    }

    protected static String getJNIRepresentation(JDefinedClass toConvert) {
        String className = toConvert.name();
        if (toConvert.outer() != null) {
            className = toConvert.outer().name() + "$" + className;
        }
        String toReturn = "L" + toConvert._package().name().replace('.', '/') + "/" + className;
        return toReturn;
    }
}
