package com.seliote.mybatis4ii;

import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * 创建 Mapper XML Action
 *
 * @author Li Yangdi
 * @since 2021-11-16
 */
public class MapperCreateAction extends AnAction {

    // Mapper XML file template
    private static final String MAPPER_TEMPLATE_PATH = "/template/mapper_template.xml";

    // Item is visible if this class in class path
    private static final String FLAG_ITEM_VISIBLE_CLASS = "org.apache.ibatis.session.Configuration";

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        dialogGetInput(project).ifPresent(input -> {
            var fileName = input.endsWith(".xml") ? input : input + ".xml";
            writeMapper(e, fileName);
        });
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        var project = e.getProject();
        var presentation = e.getPresentation();
        if (project == null) {
            presentation.setVisible(false);
            return;
        }
        var javaPsiFacade = JavaPsiFacade.getInstance(project);
        var psiClass = javaPsiFacade.findClass(
                FLAG_ITEM_VISIBLE_CLASS,
                GlobalSearchScope.allScope(project));
        presentation.setVisible(psiClass != null);
    }

    /**
     * Show dialog and get inout
     *
     * @param project Project object
     * @return User input
     */
    private Optional<String> dialogGetInput(Project project) {
        var inputDialog = new Messages.InputDialog(project,
                "Filename",
                "MyBatis Mapper XML",
                null,
                null,
                null);
        inputDialog.show();
        var input = inputDialog.getInputString();
        inputDialog.disposeIfNeeded();
        return Optional.ofNullable(input);
    }

    /**
     * Write file at context path
     *
     * @param anActionEvent AnActionEvent object
     * @param fileName      File name
     */
    private void writeMapper(AnActionEvent anActionEvent, String fileName) {
        Project project = anActionEvent.getProject();
        if (project == null) {
            Messages.showInfoMessage("Create Mapper XML failed, can not get Project", "Mapper XML");
            return;
        }
        Runnable writeMapper = () -> {
            var virtualFile = anActionEvent.getData(PlatformDataKeys.VIRTUAL_FILE);
            if (virtualFile == null) {
                Messages.showInfoMessage(
                        "Create Mapper XML failed, can not get VirtualFile", "Mapper XML");
                return;
            }
            String content;
            var resource = MapperCreateAction.class.getResourceAsStream(MAPPER_TEMPLATE_PATH);
            if (resource == null) {
                Messages.showInfoMessage(
                        "Can not read template file", "Mapper XML");
                return;
            }
            try (Reader reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
                content = StreamUtil.readText(reader);
            } catch (IOException e) {
                Messages.showInfoMessage(
                        "Create Mapper XML failed, message: " + e.getMessage(),
                        "Mapper XML");
                return;
            }
            var file = PsiFileFactory.getInstance(project)
                    .createFileFromText(fileName, XMLLanguage.INSTANCE, content);
            var psiDirectory = PsiDirectoryFactory.getInstance(project).createDirectory(virtualFile);
            psiDirectory.add(file);
        };
        WriteCommandAction.runWriteCommandAction(project, writeMapper);
    }
}
