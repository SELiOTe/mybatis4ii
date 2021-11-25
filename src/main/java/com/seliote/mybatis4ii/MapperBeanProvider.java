package com.seliote.mybatis4ii;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.spring.contexts.model.LocalAnnotationModel;
import com.intellij.spring.contexts.model.LocalModel;
import com.intellij.spring.model.CommonSpringBean;
import com.intellij.spring.model.extensions.myBatis.SpringMyBatisBeansProvider;
import com.intellij.spring.model.jam.stereotype.CustomSpringComponent;
import com.intellij.spring.model.utils.SpringCommonUtils;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Inject MyBatis Bean to Spring Container
 *
 * @author Li Yangdi
 * @since 2021-11-16
 */
public class MapperBeanProvider extends SpringMyBatisBeansProvider {

    // Detected config class
    private static final String MAPPER_SCAN = "org.mybatis.spring.annotation.MapperScan";
    // Detected config class attribute
    private static final String MAPPER_SCAN_VALUE = "value";
    private static final String MAPPER_SCAN_BASE_PACKAGES = "basePackages";
    private static final String MAPPER_SCAN_BASE_PACKAGE_CLASSES = "basePackageClasses";

    @Override
    @NotNull
    public Collection<CommonSpringBean> getCustomComponents(
            @NotNull LocalModel springModel) {
        var module = springModel.getModule();
        Collection<CommonSpringBean> mapperBeans = new LinkedList<>();
        if (module == null || DumbService.isDumb(module.getProject())) {
            return mapperBeans;
        }
        // Only support annotation config, XML config not support
        if (springModel instanceof LocalAnnotationModel) {
            var config = searchMapperScanContext(
                    (LocalAnnotationModel) springModel, module);
            if (config.isEmpty()) {
                return mapperBeans;
            }
            var psiPackages = getMapperScanPackages(config.get());
            Collection<PsiClass> mappers = scanPackagesInterface(
                    GlobalSearchScope.projectScope(module.getProject()), psiPackages);
            mappers.forEach(mapper -> mapperBeans.add(new CustomSpringComponent(mapper)));
        }
        return mapperBeans;
    }

    /**
     * Search config with MapperScan annotation
     *
     * @param springModel LocalAnnotationModel context
     * @param module      Module object
     * @return PsiClass object with MapperScan annotation
     */
    private Optional<PsiClass> searchMapperScanContext(LocalAnnotationModel springModel,
                                                       Module module) {
        if (SpringCommonUtils.findLibraryClass(module, MAPPER_SCAN) == null) {
            // MapperScan not in library
            return Optional.empty();
        }
        var config = springModel.getConfig();
        var mapperScan = config.getAnnotation(MAPPER_SCAN);
        if (mapperScan == null) {
            // PsiClass not annotated with MapperScan
            return Optional.empty();
        }
        // Actually, at there, config is a Spring Context
        return Optional.of(config);
    }

    /**
     * Get MapperScan scan package
     *
     * @param context PsiClass which annotated by MapperScan
     * @return PsiPackage config by MapperScan
     */
    private Collection<PsiPackage> getMapperScanPackages(PsiClass context) {
        Collection<PsiPackage> psiPackages = new ArrayList<>();
        var mapperScanAnnotation = context.getAnnotation(MAPPER_SCAN);
        if (mapperScanAnnotation == null) {
            return psiPackages;
        }
        var psiNameValuePairs =
                Arrays.stream(mapperScanAnnotation.getParameterList().getAttributes())
                        .filter(psiNameValuePair -> psiNameValuePair.getAttributeValue() != null)
                        .collect(Collectors.toList());
        for (var psiNameValuePair : psiNameValuePairs) {
            var attrName = psiNameValuePair.getAttributeName();
            var psiAnnotationMemberValue = psiNameValuePair.getValue();
            if (psiAnnotationMemberValue instanceof PsiArrayInitializerMemberValue) {
                psiPackages.addAll(handlePsiArrayInitializerMemberValue(
                        attrName, context, (PsiArrayInitializerMemberValue) psiAnnotationMemberValue));
            } else if (psiAnnotationMemberValue instanceof PsiLiteralExpression) {
                psiPackages.addAll(handlePsiLiteralExpression(
                        context, (PsiLiteralExpression) psiAnnotationMemberValue));
            } else if (psiAnnotationMemberValue instanceof PsiClassObjectAccessExpression) {
                psiPackages.addAll(handlePsiClassObjectAccessExpression(
                        context, (PsiClassObjectAccessExpression) psiAnnotationMemberValue));
            }
        }
        return psiPackages;
    }

    /**
     * Handle PsiArrayInitializerMemberValue object
     * like @MapperScan(basePackages = {"com.pkg"})
     * or @MapperScan(basePackageClasses = MapperMarker.class)
     *
     * @param attrName                       MapperScan attribute name
     * @param context                        Context object
     * @param psiArrayInitializerMemberValue MapperScan attribute value
     * @return MapperScan value/basePackages representative PsiPackage
     */
    private Collection<PsiPackage> handlePsiArrayInitializerMemberValue(
            String attrName,
            PsiClass context,
            PsiArrayInitializerMemberValue psiArrayInitializerMemberValue) {
        Collection<PsiPackage> psiPackages = new ArrayList<>();
        if (MAPPER_SCAN_VALUE.equals(attrName)
                || MAPPER_SCAN_BASE_PACKAGES.equals(attrName)) {
            psiPackages.addAll(handleMapperScanValue(context,
                    psiArrayInitializerMemberValue));
        } else if (MAPPER_SCAN_BASE_PACKAGE_CLASSES.equals(attrName)) {
            psiPackages.addAll(handleMapperScanClass(context,
                    psiArrayInitializerMemberValue));
        }
        return psiPackages;
    }

    /**
     * Handle PsiLiteralExpression object, like @MapperScan(basePackages = "com.pkg")
     *
     * @param context              Context object
     * @param psiLiteralExpression MapperScan attribute value
     * @return MapperScan value representative PsiPackage
     */
    private Collection<PsiPackage> handlePsiLiteralExpression(
            PsiClass context,
            PsiLiteralExpression psiLiteralExpression) {
        Collection<PsiPackage> psiPackages = new ArrayList<>();
        var psiPackage = getPackageByName(
                JavaPsiFacade.getInstance(context.getProject()),
                // Value is a String but with quotation mark at begin and end
                psiLiteralExpression.getText().replaceAll("\"", ""));
        psiPackage.ifPresent(psiPackages::add);
        return psiPackages;
    }

    /**
     * Handle PsiLiteralExpression object, like @MapperScan(basePackageClasses = MapperMarker.class)
     *
     * @param context                        Context object
     * @param psiClassObjectAccessExpression MapperScan attribute value
     * @return MapperScan basePackages representative PsiPackage
     */
    private Collection<PsiPackage> handlePsiClassObjectAccessExpression(
            PsiClass context,
            PsiClassObjectAccessExpression psiClassObjectAccessExpression) {
        Collection<PsiPackage> psiPackages = new ArrayList<>();
        var psiClass = PsiTypesUtil.getPsiClass(psiClassObjectAccessExpression.getOperand().getType());
        if (psiClass != null) {
            String packageName = ((PsiJavaFile) psiClass.getContainingFile())
                    .getPackageName();
            var psiPackage = getPackageByName(
                    JavaPsiFacade.getInstance(context.getProject()), packageName);
            psiPackage.ifPresent(psiPackages::add);
        }
        return psiPackages;
    }

    /**
     * Get PsiPackage classes by MapperScan value/basePackages
     *
     * @param context                        PsiClass which annotated by MapperScan
     * @param psiArrayInitializerMemberValue PsiArrayInitializerMemberValue data object
     * @return MapperScan value/basePackages representative PsiPackage
     */
    private Collection<PsiPackage> handleMapperScanValue(
            PsiClass context,
            PsiArrayInitializerMemberValue psiArrayInitializerMemberValue) {
        Collection<PsiPackage> psiPackages = new ArrayList<>();
        for (var value : psiArrayInitializerMemberValue.getInitializers()) {
            var psiPackage =
                    getPackageByName(
                            JavaPsiFacade.getInstance(context.getProject()),
                            // Value is a String but with quotation mark at begin and end
                            value.getText().replaceAll("\"", ""));
            psiPackage.ifPresent(psiPackages::add);
        }
        return psiPackages;
    }

    /**
     * Get PsiPackage classes by MapperScan basePackageClasses
     *
     * @param context                        PsiClass which annotated by MapperScan
     * @param psiArrayInitializerMemberValue PsiArrayInitializerMemberValue data object
     * @return MapperScan basePackageClasses representative PsiPackage
     */
    private Collection<PsiPackage> handleMapperScanClass(
            PsiClass context,
            PsiArrayInitializerMemberValue psiArrayInitializerMemberValue) {
        Collection<PsiPackage> psiPackages = new ArrayList<>();
        for (var value : psiArrayInitializerMemberValue.getInitializers()) {
            // 通过属性值中的类来获取所在包的 PsiPackage 对象
            if (value instanceof PsiClassObjectAccessExpression) {
                var psiTypeElement
                        = ((PsiClassObjectAccessExpression) value).getOperand();
                var psiClass = PsiTypesUtil.getPsiClass(psiTypeElement.getType());
                if (psiClass != null) {
                    String packageName = ((PsiJavaFile) psiClass.getContainingFile())
                            .getPackageName();
                    var psiPackage = getPackageByName(
                            JavaPsiFacade.getInstance(context.getProject()), packageName);
                    psiPackage.ifPresent(psiPackages::add);
                }
            }
        }
        return psiPackages;
    }

    /**
     * Get PsiPackage class by package name
     *
     * @param javaPsiFacade JavaPsiFacade object
     * @param packageName   package name
     * @return PsiPackage object
     */
    private Optional<PsiPackage> getPackageByName(JavaPsiFacade javaPsiFacade,
                                                  String packageName) {
        if (packageName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(javaPsiFacade.findPackage(packageName));
    }

    /**
     * Scan all interface in packages
     *
     * @param scope       GlobalSearchScope object
     * @param psiPackages packages to scan
     * @return PsiClass object collection
     */
    private Collection<PsiClass> scanPackagesInterface(GlobalSearchScope scope,
                                                       Collection<PsiPackage> psiPackages) {
        Collection<PsiClass> mappers = new ArrayList<>();
        for (var psiPackage : psiPackages) {
            mappers.addAll(
                    Arrays.stream(psiPackage.getClasses(scope))
                            .filter(PsiClass::isInterface)
                            .collect(Collectors.toList())
            );
        }
        return mappers;
    }
}
