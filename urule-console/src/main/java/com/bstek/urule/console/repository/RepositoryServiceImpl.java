package com.bstek.urule.console.repository;

import com.bstek.urule.Utils;
import com.bstek.urule.console.DefaultUser;
import com.bstek.urule.console.User;
import com.bstek.urule.console.exception.NoPermissionException;
import com.bstek.urule.console.repository.model.FileType;
import com.bstek.urule.console.repository.model.LibType;
import com.bstek.urule.console.repository.model.RepositoryFile;
import com.bstek.urule.console.repository.model.Type;
import com.bstek.urule.console.repository.permission.PermissionService;
import com.bstek.urule.console.repository.refactor.RefactorService;
import com.bstek.urule.console.repository.refactor.RefactorServiceImpl;
import com.bstek.urule.console.servlet.permission.ProjectConfig;
import com.bstek.urule.console.servlet.permission.UserPermission;
import com.bstek.urule.exception.RuleException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.jackrabbit.value.BinaryImpl;
import org.apache.jackrabbit.value.DateValue;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import javax.jcr.*;
import javax.jcr.lock.Lock;
import javax.jcr.nodetype.NodeType;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * @author Jacky.gao
 * 2016年5月24日
 */
public class RepositoryServiceImpl extends BaseRepositoryService implements RepositoryService, ApplicationContextAware {
    protected RefactorService refactorService;
    private PermissionService permissionService;

    @Override
    public List<UserPermission> loadResourceSecurityConfigs(String companyId) throws Exception {
        List<UserPermission> configs = new ArrayList<>();
        String filePath = RESOURCE_SECURITY_CONFIG_FILE + (companyId == null ? "" : companyId);
        Node rootNode = getRootNode();
        Node fileNode = rootNode.getNode(filePath);
        Property property = fileNode.getProperty(DATA);
        Binary fileBinary = property.getBinary();
        InputStream inputStream = fileBinary.getStream();
        String content = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        inputStream.close();
        Document document = DocumentHelper.parseText(content);
        Element rootElement = document.getRootElement();
        for (Object obj : rootElement.elements()) {
            if (!(obj instanceof Element)) {
                continue;
            }
            Element element = (Element) obj;
            if (!element.getName().equals("user-permission")) {
                continue;
            }
            UserPermission userResource = new UserPermission();
            userResource.setUsername(element.attributeValue("username"));
            userResource.setProjectConfigs(parseProjectConfigs(element));
            configs.add(userResource);
        }
        return configs;
    }

    private List<ProjectConfig> parseProjectConfigs(Element element) {
        List<ProjectConfig> list = new ArrayList<ProjectConfig>();
        for (Object obj : element.elements()) {
            if (!(obj instanceof Element)) {
                continue;
            }
            Element ele = (Element) obj;
            if (!ele.getName().equals("project-config")) {
                continue;
            }
            ProjectConfig config = new ProjectConfig();
            config.setProject(ele.attributeValue("project"));
            config.setReadProject(parseBooleanValue(ele, "read-project"));

            config.setReadPackage(parseBooleanValue(ele, "read-package"));
            config.setWritePackage(parseBooleanValue(ele, "write-package"));

            config.setReadVariableFile(parseBooleanValue(ele, "read-variable-file"));
            config.setWriteVariableFile(parseBooleanValue(ele, "write-variable-file"));

            config.setReadParameterFile(parseBooleanValue(ele, "read-parameter-file"));
            config.setWriteParameterFile(parseBooleanValue(ele, "write-parameter-file"));

            config.setReadConstantFile(parseBooleanValue(ele, "read-constant-file"));
            config.setWriteConstantFile(parseBooleanValue(ele, "write-constant-file"));

            config.setReadActionFile(parseBooleanValue(ele, "read-action-file"));
            config.setWriteActionFile(parseBooleanValue(ele, "write-action-file"));

            config.setReadRuleFile(parseBooleanValue(ele, "read-rule-file"));
            config.setWriteRuleFile(parseBooleanValue(ele, "write-rule-file"));

            config.setReadScorecardFile(parseBooleanValue(ele, "read-scorecard-file"));
            config.setWriteScorecardFile(parseBooleanValue(ele, "write-scorecard-file"));

            config.setReadDecisionTableFile(parseBooleanValue(ele, "read-decision-table-file"));
            config.setWriteDecisionTableFile(parseBooleanValue(ele, "write-decision-table-file"));

            config.setReadDecisionTreeFile(parseBooleanValue(ele, "read-decision-tree-file"));
            config.setWriteDecisionTreeFile(parseBooleanValue(ele, "write-decision-tree-file"));

            config.setReadFlowFile(parseBooleanValue(ele, "read-flow-file"));
            config.setWriteFlowFile(parseBooleanValue(ele, "write-flow-file"));
            list.add(config);
        }
        return list;
    }

    private boolean parseBooleanValue(Element element, String attributeName) {
        if (element.attributeValue(attributeName) != null) {
            return Boolean.parseBoolean(element.attributeValue(attributeName));
        }
        return false;
    }

    @Override
    public String saveTemplateFile(String path, String content) throws Exception {
        path = this.processPath(path);
        Node rootNode = this.getRootNode();
        if (!rootNode.hasNode(path)) {
            throw new RuleException("File [" + path + "] not exist.");
        } else {
            Node fileNode = rootNode.getNode(path);
            Binary fileBinary = new BinaryImpl(content.getBytes(StandardCharsets.UTF_8));
            fileNode.setProperty("_data", fileBinary);
            fileNode.setProperty("_file", true);
            this.session.save();
            return path;
        }
    }

    @Override
    public String getProject(String path) {
        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        int pos = path.indexOf("/");
        if (pos == -1) {
            pos = path.length();
        }

        return path.substring(0, pos);
    }

    @Override
    public List<RepositoryFile> loadTemplates(String project) throws Exception {
        String path = project + "/" + "__rules_templates__";
        path = this.processPath(path);
        Node rootNode = this.getRootNode();
        Node fileNode = null;
        if (!rootNode.hasNode(path)) {
            fileNode = rootNode.addNode(path);
        } else {
            fileNode = rootNode.getNode(path);
        }

        List<RepositoryFile> files = new ArrayList<>();
        NodeIterator nodeIterator = fileNode.getNodes();

        while (nodeIterator.hasNext()) {
            Node categoryNode = nodeIterator.nextNode();
            if (categoryNode.hasProperty("_template_category")) {
                RepositoryFile categoryFile = new RepositoryFile();
                categoryFile.setName(categoryNode.getName());
                categoryFile.setFullPath(categoryNode.getPath());
                categoryFile.setChildren(this.loadTemplateFiles(categoryNode));
                files.add(categoryFile);
            }
        }

        return files;
    }

    private List<RepositoryFile> loadTemplateFiles(Node categoryNode) throws Exception {
        List<RepositoryFile> list = new ArrayList<>();
        NodeIterator nodeIterator = categoryNode.getNodes();

        while (nodeIterator.hasNext()) {
            Node fileNode = nodeIterator.nextNode();
            if (fileNode.hasProperty("_file")) {
                RepositoryFile file = new RepositoryFile();
                file.setType(Type.rule);
                file.setFullPath(fileNode.getPath());
                file.setName(fileNode.getName());
                if (fileNode.hasProperty("__rules_template_comment__")) {
                    file.setComment(fileNode.getProperty("__rules_template_comment__").getString());
                }

                list.add(file);
            }
        }

        return list;
    }

    @Override
    public List<ClientConfig> loadClientConfigs(String project) throws Exception {
        if (!permissionService.isAdmin()) {
            throw new NoPermissionException();
        }
        List<ClientConfig> clients = new ArrayList<>();
        Node rootNode = getRootNode();
        String filePath = processPath(project) + "/" + CLIENT_CONFIG_FILE;
        Node fileNode = rootNode.getNode(filePath);
        Property property = fileNode.getProperty(DATA);
        Binary fileBinary = property.getBinary();
        InputStream inputStream = fileBinary.getStream();
        String content = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        inputStream.close();
        Document document = DocumentHelper.parseText(content);
        Element rootElement = document.getRootElement();
        for (Object obj : rootElement.elements()) {
            if (!(obj instanceof Element)) {
                continue;
            }
            Element element = (Element) obj;
            if (!element.getName().equals("item")) {
                continue;
            }
            ClientConfig client = new ClientConfig();
            client.setName(element.attributeValue("name"));
            client.setClient(element.attributeValue("client"));
            client.setProject(project);
            clients.add(client);
        }
        return clients;
    }

    @Override
    public PackageConfig loadPackageConfigs(String project) throws Exception {
        Node rootNode = getRootNode();
        String filePath = processPath(project) + "/" + PACKAGE_CONFIG_FILE;

        // 判断文件是否存在
        if (!fileExist(filePath)) {
            DefaultUser defaultUser = new DefaultUser();
            defaultUser.setUsername("system");
            defaultUser.setAdmin(true);
            createPackageConfigFile(project, defaultUser);
        }

        Node fileNode = rootNode.getNode(filePath);
        Property property = fileNode.getProperty(DATA);
        Binary fileBinary = property.getBinary();
        InputStream inputStream = fileBinary.getStream();
        String content = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        inputStream.close();

        Document document = DocumentHelper.parseText(content);
        Element rootElement = document.getRootElement();

        PackageConfig packageConfig = new PackageConfig();
        packageConfig.setVersion(rootElement.attributeValue("version"));
        packageConfig.setLock(Boolean.parseBoolean(rootElement.attributeValue("lock")));
        return packageConfig;
    }

    @Override
    public void updatePackageConfigs(String project, PackageConfig packageConfig) throws Exception {
        Node rootNode = getRootNode();
        String filePath = processPath(project) + "/" + PACKAGE_CONFIG_FILE;

        Node fileNode = rootNode.getNode(filePath);
        Property property = fileNode.getProperty(DATA);
        Binary fileBinary = property.getBinary();
        InputStream inputStream = fileBinary.getStream();
        String content = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        inputStream.close();

        Document document = DocumentHelper.parseText(content);
        Element rootElement = document.getRootElement();
        rootElement.setAttributeValue("version", packageConfig.getVersion());
        rootElement.setAttributeValue("lock", packageConfig.getLock().toString());

        DefaultUser defaultUser = new DefaultUser();
        defaultUser.setUsername("system");
        defaultUser.setAdmin(true);
        saveFile(filePath, document.asXML(), false, null, defaultUser);
    }

    @Override
    public List<RepositoryFile> getDirectories(String project) throws Exception {
        Node rootNode = getRootNode();
        NodeIterator nodeIterator = rootNode.getNodes();
        Node targetProjectNode = null;
        while (nodeIterator.hasNext()) {
            Node projectNode = nodeIterator.nextNode();
            if (!projectNode.hasProperty(FILE)) {
                continue;
            }
            String projectName = projectNode.getName();
            if (project != null && !project.equals(projectName)) {
                continue;
            }
            targetProjectNode = projectNode;
            break;
        }
        if (targetProjectNode == null) {
            throw new RuleException("Project [" + project + "] not exist.");
        }
        List<RepositoryFile> fileList = new ArrayList<RepositoryFile>();
        RepositoryFile root = new RepositoryFile();
        root.setName("根目录");
        String projectPath = targetProjectNode.getPath();
        root.setFullPath(projectPath);
        fileList.add(root);
        NodeIterator projectNodeIterator = targetProjectNode.getNodes();
        while (projectNodeIterator.hasNext()) {
            Node dirNode = projectNodeIterator.nextNode();
            if (!dirNode.hasProperty(DIR_TAG)) {
                continue;
            }
            RepositoryFile file = new RepositoryFile();
            file.setName(dirNode.getPath().substring(projectPath.length()));
            file.setFullPath(dirNode.getPath());
            fileList.add(file);
            buildDirectories(dirNode, fileList, projectPath);
        }
        return fileList;
    }

    private void buildDirectories(Node node, List<RepositoryFile> fileList, String projectPath) throws Exception {
        NodeIterator nodeIterator = node.getNodes();
        while (nodeIterator.hasNext()) {
            Node dirNode = nodeIterator.nextNode();
            if (!dirNode.hasProperty(FILE)) {
                continue;
            }
            if (!dirNode.hasProperty(DIR_TAG)) {
                continue;
            }
            RepositoryFile file = new RepositoryFile();
            file.setName(dirNode.getPath().substring(projectPath.length()));
            file.setFullPath(dirNode.getPath());
            buildDirectories(dirNode, fileList, projectPath);
            fileList.add(file);
        }
    }

    @Override
    public Repository loadRepository(String project, User user, boolean classify, FileType[] types, String searchFileName) throws Exception {
        String companyId = user.getCompanyId();
        createSecurityConfigFile(user);
        if (project != null && project.startsWith("/")) {
            project = project.substring(1);
        }
        Repository repo = new Repository();
        List<String> projectNames = new ArrayList<>();
        repo.setProjectNames(projectNames);
        RepositoryFile rootFile = new RepositoryFile();
        rootFile.setFullPath("/");
        rootFile.setName("项目列表");
        rootFile.setType(Type.root);
        Node rootNode = getRootNode();
        NodeIterator nodeIterator = rootNode.getNodes();
        while (nodeIterator.hasNext()) {
            Node projectNode = nodeIterator.nextNode();
            if (!projectNode.hasProperty(FILE)) {
                continue;
            }
            if (StringUtils.isNotEmpty(companyId)) {
                if (projectNode.hasProperty(COMPANY_ID)) {
                    String id = projectNode.getProperty(COMPANY_ID).getString();
                    if (!companyId.equals(id)) {
                        continue;
                    }
                }
            }
            String projectName = projectNode.getName();
            if (projectName.contains(RESOURCE_SECURITY_CONFIG_FILE)) {
                continue;
            }
            if (StringUtils.isNotBlank(project) && !project.equals(projectName)) {
                continue;
            }
            if (!permissionService.projectHasPermission(projectNode.getPath())) {
                continue;
            }
            if (StringUtils.isBlank(project)) {
                projectNames.add(projectName);
            }
            RepositoryFile projectFile = buildProjectFile(projectNode, types, classify, searchFileName);
            rootFile.addChild(projectFile, false);
        }
        repo.setRootFile(rootFile);
        return repo;
    }

    private RepositoryFile buildProjectFile(Node projectNode, FileType[] types, boolean classify, String searchFileName) throws Exception {
        RepositoryFile projectFile = new RepositoryFile();
        projectFile.setType(Type.project);
        projectFile.setName(projectNode.getName());
        projectFile.setFullPath("/" + projectNode.getName());
        RepositoryFile resDir = new RepositoryFile();
        resDir.setFullPath(projectFile.getFullPath());
        resDir.setName("资源");
        if ((types == null || types.length == 0) && permissionService.projectPackageHasReadPermission(projectNode.getPath())) {
            RepositoryFile packageFile = new RepositoryFile();
            packageFile.setName("知识包.rp");
            packageFile.setType(Type.resourcePackage);
            packageFile.setFullPath(projectFile.getFullPath() + "/" + RES_PACKGE_FILE);
            projectFile.addChild(packageFile, false);
        }
        if (classify) {
            resDir.setType(Type.resource);
            createResourceCategory(projectNode, resDir, types, searchFileName);
        } else {
            resDir.setType(Type.all);
            buildResources(projectNode, resDir, types, searchFileName);
        }
        projectFile.addChild(resDir, false);
        return projectFile;
    }

    private void buildResources(Node projectNode, RepositoryFile libDir, FileType[] types, String searchFileName) throws Exception {
        FileType[] fileTypes = types;
        if (types == null || types.length == 0) {
            fileTypes = new FileType[]{FileType.VariableLibrary,
                    FileType.ParameterLibrary, FileType.ConstantLibrary,
                    FileType.ActionLibrary, FileType.Ruleset, FileType.RulesetLib,
                    FileType.RuleFlow, FileType.DecisionTable,
                    FileType.DecisionTree, FileType.ScriptDecisionTable,
                    FileType.UL, FileType.Scorecard, FileType.ComplexScorecard};
        }
        libDir.setLibType(LibType.all);
        buildNodes(projectNode.getNodes(), libDir, fileTypes, Type.all, searchFileName);
    }

    private void createResourceCategory(Node projectNode, RepositoryFile libDir, FileType[] types, String searchFileName) throws Exception {
        RepositoryFile subLib = buildLibFile(libDir, "库", LibType.res);
        subLib.setType(Type.lib);
        libDir.addChild(subLib, false);
        FileType[] librarySubTypes = types;
        if (types == null || types.length == 0) {
            librarySubTypes = new FileType[]{FileType.VariableLibrary, FileType.ParameterLibrary, FileType.ConstantLibrary, FileType.ActionLibrary};
        }
        buildNodes(projectNode.getNodes(), subLib, librarySubTypes, Type.lib, searchFileName);

        RepositoryFile rulesLib = buildLibFile(libDir, "决策集", LibType.ruleset);
        rulesLib.setFullPath(libDir.getFullPath());
        rulesLib.setType(Type.ruleLib);

        RepositoryFile decisionTableLib = buildLibFile(libDir, "决策表", LibType.decisiontable);
        decisionTableLib.setFullPath(libDir.getFullPath());
        decisionTableLib.setType(Type.decisionTableLib);

        RepositoryFile decisionTreeLib = buildLibFile(libDir, "决策树", LibType.decisiontree);
        decisionTreeLib.setFullPath(libDir.getFullPath());
        decisionTreeLib.setType(Type.decisionTreeLib);

        RepositoryFile scorecardLib = buildLibFile(libDir, "评分卡", LibType.scorecard);
        scorecardLib.setFullPath(libDir.getFullPath());
        scorecardLib.setType(Type.scorecardLib);

        RepositoryFile flowLib = buildLibFile(libDir, "决策流", LibType.ruleflow);
        flowLib.setFullPath(libDir.getFullPath());
        flowLib.setType(Type.flowLib);

        libDir.addChild(rulesLib, false);
        libDir.addChild(decisionTableLib, false);
        libDir.addChild(decisionTreeLib, false);
        libDir.addChild(scorecardLib, false);
        libDir.addChild(flowLib, false);

        FileType[] libraryRuleTypes = types;
        if (types == null || types.length == 0) {
            libraryRuleTypes = new FileType[]{FileType.Ruleset, FileType.RulesetLib, FileType.UL};
        }

        FileType[] libraryDecisionTypes = types;
        if (types == null || types.length == 0) {
            libraryDecisionTypes = new FileType[]{FileType.DecisionTable, FileType.ScriptDecisionTable};
        }
        FileType[] libraryDecisionTreeTypes = types;
        if (types == null || types.length == 0) {
            libraryDecisionTreeTypes = new FileType[]{FileType.DecisionTree};
        }

        FileType[] libraryFlowTypes = types;
        if (types == null || types.length == 0) {
            libraryFlowTypes = new FileType[]{FileType.RuleFlow};
        }

        FileType[] libraryScorecardTypes = types;
        if (types == null || types.length == 0) {
            libraryScorecardTypes = new FileType[]{FileType.Scorecard, FileType.ComplexScorecard};
        }

        buildNodes(projectNode.getNodes(), rulesLib, libraryRuleTypes, Type.ruleLib, searchFileName);
        buildNodes(projectNode.getNodes(), decisionTableLib, libraryDecisionTypes, Type.decisionTableLib, searchFileName);
        buildNodes(projectNode.getNodes(), decisionTreeLib, libraryDecisionTreeTypes, Type.decisionTreeLib, searchFileName);
        buildNodes(projectNode.getNodes(), scorecardLib, libraryScorecardTypes, Type.scorecardLib, searchFileName);
        buildNodes(projectNode.getNodes(), flowLib, libraryFlowTypes, Type.flowLib, searchFileName);
    }

    private RepositoryFile buildLibFile(RepositoryFile libraryDir, String name, LibType libType) {
        RepositoryFile subLib = new RepositoryFile();
        subLib.setFullPath(libraryDir.getFullPath());
        subLib.setName(name);
        subLib.setLibType(libType);
        return subLib;
    }

    private void buildNodes(NodeIterator nodeIterator, RepositoryFile parent, FileType[] types, Type folderType, String searchFileName) throws Exception {
        LibType libType = parent.getLibType();
        while (nodeIterator.hasNext()) {
            Node fileNode = nodeIterator.nextNode();
            if (!fileNode.hasProperty(FILE)) {
                continue;
            }
            RepositoryFile file = new RepositoryFile();
            file.setLibType(libType);
            String name = fileNode.getName();
            if (name.toLowerCase().contains(RES_PACKGE_FILE)
                    || name.toLowerCase().contains(PACKAGE_CONFIG_FILE)
                    || name.toLowerCase().contains(CLIENT_CONFIG_FILE)
                    || name.toLowerCase().contains(RESOURCE_SECURITY_CONFIG_FILE)) {
                continue;
            }
            if (!fileNode.hasProperty(DIR_TAG)) {
                if (!permissionService.fileHasReadPermission(fileNode.getPath())) {
                    continue;
                }
                FileType fileType = null;
                boolean add = false;
                for (FileType type : types) {
                    if (name.toLowerCase().endsWith(type.toString())) {
                        fileType = type;
                        add = true;
                        break;
                    }
                }
                if (!add) {
                    continue;
                }

                if (libType.equals(LibType.res)) {
                    if (!fileType.equals(FileType.ActionLibrary) && !fileType.equals(FileType.ParameterLibrary) && !fileType.equals(FileType.ConstantLibrary) && !fileType.equals(FileType.VariableLibrary)) {
                        continue;
                    }
                }

                if (libType.equals(LibType.decisiontable)) {
                    if (!fileType.equals(FileType.ScriptDecisionTable) && !fileType.equals(FileType.DecisionTable)) {
                        continue;
                    }
                }

                if (libType.equals(LibType.decisiontree)) {
                    if (!fileType.equals(FileType.DecisionTree)) {
                        continue;
                    }
                }

                if (libType.equals(LibType.ruleflow)) {
                    if (!fileType.equals(FileType.RuleFlow)) {
                        continue;
                    }
                }

                if (libType.equals(LibType.scorecard)) {
                    if (!fileType.equals(FileType.Scorecard) && !fileType.equals(FileType.ComplexScorecard)) {
                        continue;
                    }
                }

                if (libType.equals(LibType.ruleset)) {
                    if (!fileType.equals(FileType.Ruleset) && !fileType.equals(FileType.UL) && !fileType.equals(FileType.RulesetLib)) {
                        continue;
                    }
                }

                if (StringUtils.isNotBlank(searchFileName)) {
                    boolean fileNameContain = name.toLowerCase().contains(searchFileName.toLowerCase());
                    if (name.toLowerCase().endsWith(FileType.Ruleset.toString())) {
                        // 搜索文件本身
                        try {
                            InputStream inputStream = null;
                            inputStream = readFile(fileNode.getPath());


                            byte[] bytes = new byte[0];
                            bytes = new byte[inputStream.available()];
                            inputStream.read(bytes);
                            String ruleContent = new String(bytes);

                            if (!ruleContent.toLowerCase().contains(searchFileName.toLowerCase()) && !fileNameContain) {
                                continue;
                            }
                        } catch (Exception ex) {
                        }
                    } else {
                        // 搜索文件名
                        if (!fileNameContain) {
                            continue;
                        }
                    }
                }

                if (name.toLowerCase().endsWith(FileType.ActionLibrary.toString())) {
                    file.setType(Type.action);
                } else if (name.toLowerCase().endsWith(FileType.VariableLibrary.toString())) {
                    file.setType(Type.variable);
                } else if (name.toLowerCase().endsWith(FileType.ConstantLibrary.toString())) {
                    file.setType(Type.constant);
                } else if (name.toLowerCase().endsWith(FileType.Ruleset.toString())) {
                    file.setType(Type.rule);
                } else if (name.toLowerCase().endsWith(FileType.RulesetLib.toString())) {
                    file.setType(Type.rule);
                } else if (name.toLowerCase().endsWith(FileType.DecisionTable.toString())) {
                    file.setType(Type.decisionTable);
                } else if (name.toLowerCase().endsWith(FileType.UL.toString())) {
                    file.setType(Type.ul);
                } else if (name.toLowerCase().endsWith(FileType.ParameterLibrary.toString())) {
                    file.setType(Type.parameter);
                } else if (name.toLowerCase().endsWith(FileType.RuleFlow.toString())) {
                    file.setType(Type.flow);
                } else if (name.toLowerCase().endsWith(FileType.ScriptDecisionTable.toString())) {
                    file.setType(Type.scriptDecisionTable);
                } else if (name.toLowerCase().endsWith(FileType.DecisionTree.toString())) {
                    file.setType(Type.decisionTree);
                } else if (name.toLowerCase().endsWith(FileType.Scorecard.toString())) {
                    file.setType(Type.scorecard);
                } else if (name.toLowerCase().endsWith(FileType.ComplexScorecard.toString())) {
                    file.setType(Type.complexscorecard);
                }
                file.setFullPath(fileNode.getPath());
                file.setName(name);
                buildNodeLockInfo(fileNode, file);
                parent.addChild(file, false);
                buildNodes(fileNode.getNodes(), file, types, folderType, searchFileName);
            } else {
                file.setFullPath(fileNode.getPath());
                file.setName(name);
                file.setType(Type.folder);
                buildNodeLockInfo(fileNode, file);
                file.setFolderType(folderType);
                parent.addChild(file, true);
                buildNodes(fileNode.getNodes(), file, types, folderType, searchFileName);
            }
        }
    }

    private void buildNodeLockInfo(Node node, RepositoryFile file) throws Exception {
        String absPath = node.getPath();
        if (!lockManager.isLocked(absPath)) {
            return;
        }
        String owner = lockManager.getLock(absPath).getLockOwner();
        file.setLock(true);
        file.setLockInfo("被" + owner + "锁定");
    }

    @Override
    public void lockPath(String path, User user) throws Exception {
        path = processPath(path);
        int pos = path.indexOf(":");
        if (pos != -1) {
            path = path.substring(0, pos);
        }
        Node rootNode = getRootNode();
        if (!rootNode.hasNode(path)) {
            throw new RuleException("File [" + path + "] not exist.");
        }
        Node fileNode = rootNode.getNode(path);
        String topAbsPath = fileNode.getPath();
        if (lockManager.isLocked(topAbsPath)) {
            String owner = lockManager.getLock(topAbsPath).getLockOwner();
            throw new NodeLockException("【" + path + "】已被" + owner + "锁定，您不能进行再次锁定!");
        }
        List<Node> nodeList = new ArrayList<Node>();
        unlockAllChildNodes(fileNode, user, nodeList, path);
        for (Node node : nodeList) {
            if (!lockManager.isLocked(node.getPath())) {
                continue;
            }
            Lock lock = lockManager.getLock(node.getPath());
            lockManager.unlock(lock.getNode().getPath());
        }
        if (!fileNode.isNodeType(NodeType.MIX_LOCKABLE)) {
            if (!fileNode.isCheckedOut()) {
                versionManager.checkout(fileNode.getPath());
            }
            fileNode.addMixin("mix:lockable");
            session.save();
        }
        lockManager.lock(topAbsPath, true, true, Long.MAX_VALUE, user.getUsername());
    }

    private void unlockAllChildNodes(Node node, User user, List<Node> nodeList, String rootPath) throws Exception {
        NodeIterator iter = node.getNodes();
        while (iter.hasNext()) {
            Node nextNode = iter.nextNode();
            String absPath = nextNode.getPath();
            if (!lockManager.isLocked(absPath)) {
                continue;
            }
            Lock lock = lockManager.getLock(absPath);
            String owner = lock.getLockOwner();
            if (!user.getUsername().equals(owner)) {
                throw new NodeLockException("当前目录下有子目录被其它人锁定，您不能执行锁定" + rootPath + "目录");
            }
            nodeList.add(nextNode);
            unlockAllChildNodes(nextNode, user, nodeList, rootPath);
        }
    }

    @Override
    public void unlockPath(String path, User user) throws Exception {
        path = processPath(path);
        int pos = path.indexOf(":");
        if (pos != -1) {
            path = path.substring(0, pos);
        }
        Node rootNode = getRootNode();
        if (!rootNode.hasNode(path)) {
            throw new RuleException("File [" + path + "] not exist.");
        }
        Node fileNode = rootNode.getNode(path);
        String absPath = fileNode.getPath();
        if (!lockManager.isLocked(absPath)) {
            throw new NodeLockException("当前文件未锁定，不需要解锁!");
        }
        Lock lock = lockManager.getLock(absPath);
        String owner = lock.getLockOwner();
        if (!owner.equals(user.getUsername())) {
            throw new NodeLockException("当前文件由【" + owner + "】锁定，您无权解锁!");
        }
        lockManager.unlock(lock.getNode().getPath());
    }

    @Override
    public void deleteFile(String path, User user) throws Exception {
        if (!permissionService.fileHasWritePermission(path)) {
            throw new NoPermissionException();
        }
        repositoryInteceptor.deleteFile(path);
        path = processPath(path);
        Node rootNode = getRootNode();
        if (!rootNode.hasNode(path)) {
            throw new RuleException("File [" + path + "] not exist.");
        }
        String[] subpaths = path.split("/");
        Node fileNode = rootNode;
        for (String subpath : subpaths) {
            if (StringUtils.isEmpty(subpath)) {
                continue;
            }
            String subDirs[] = subpath.split("\\.");
            for (String dir : subDirs) {
                if (StringUtils.isEmpty(dir)) {
                    continue;
                }
                if (!fileNode.hasNode(dir)) {
                    continue;
                }
                fileNode = fileNode.getNode(dir);
                lockCheck(fileNode, user);
                if (!fileNode.isCheckedOut()) {
                    versionManager.checkout(fileNode.getPath());
                }
            }
        }
        fileNode = rootNode.getNode(path);
        lockCheck(fileNode, user);
        if (!fileNode.isCheckedOut()) {
            versionManager.checkout(fileNode.getPath());
        }
        fileNode.remove();
        session.save();
    }

    @Override
    public void saveFile(String path, String content, boolean newVersion, String versionComment, User user) throws Exception {
        saveFile(path, content, newVersion, versionComment, null, null, user);
    }

    @Override
    public void saveFile(String path, String content, boolean newVersion, String versionComment, String beforeComment, String afterComment, User user) throws Exception {
        path = Utils.decodeURL(path);
        if (path.contains(RES_PACKGE_FILE)) {
            if (!permissionService.projectPackageHasWritePermission(path)) {
                throw new NoPermissionException();
            }
        }
        if (!permissionService.fileHasWritePermission(path)) {
            throw new NoPermissionException();
        }

        repositoryInteceptor.saveFile(path, content);
        path = processPath(path);
        int pos = path.indexOf(":");
        if (pos != -1) {
            path = path.substring(0, pos);
        }
        Node rootNode = getRootNode();
        if (!rootNode.hasNode(path)) {
            throw new RuleException("File [" + path + "] not exist.");
        }
        Node fileNode = rootNode.getNode(path);
        lockCheck(fileNode, user);
        versionManager.checkout(fileNode.getPath());
        Binary fileBinary = new BinaryImpl(content.getBytes(StandardCharsets.UTF_8));
        fileNode.setProperty(DATA, fileBinary);
        fileNode.setProperty(FILE, true);
        fileNode.setProperty(CRATE_USER, user.getUsername());
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        DateValue dateValue = new DateValue(calendar);
        fileNode.setProperty(CRATE_DATE, dateValue);
        if (newVersion && StringUtils.isNotBlank(versionComment)) {
            fileNode.setProperty(VERSION_COMMENT, versionComment);
            if (StringUtils.isNotBlank(beforeComment)) {
                fileNode.setProperty(BEFORE_COMMENT, beforeComment);
            }
            if (StringUtils.isNotBlank(afterComment)) {
                fileNode.setProperty(AFTER_COMMENT, afterComment);
            }
        }
        session.save();
        if (newVersion) {
            versionManager.checkin(fileNode.getPath());
        }
    }

    @Override
    public List<String> getReferenceFiles(String path, String searchText) throws Exception {
        Node rootNode = getRootNode();
        List<String> referenceFiles = new ArrayList<>();
        List<String> files = getFiles(rootNode, path);
        for (String nodePath : files) {
            InputStream inputStream = readFile(nodePath, null);
            try {
                String content = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
                inputStream.close();
                boolean containPath = content.contains(path);
                boolean containText = content.contains(searchText);
                if (containPath && containText) {
                    referenceFiles.add(nodePath);
                }
            } catch (IOException e) {
                throw new RuleException(e);
            }
        }
        return referenceFiles;
    }

    private List<String> getFiles(Node rootNode, String path) throws Exception {
        String project = getProjects(path);
        List<String> list = new ArrayList<>();
        Node projectNode = rootNode.getNode(project);
        buildPath(list, projectNode);
        return list;
    }

    private void buildPath(List<String> list, Node parentNode) throws RepositoryException {
        NodeIterator nodeIterator = parentNode.getNodes();
        while (nodeIterator.hasNext()) {
            Node node = nodeIterator.nextNode();
            String nodePath = node.getPath();
            if (nodePath.endsWith(FileType.Ruleset.toString())) {
                list.add(nodePath);
            } else if (nodePath.endsWith(FileType.UL.toString())) {
                list.add(nodePath);
            } else if (nodePath.endsWith(FileType.DecisionTable.toString())) {
                list.add(nodePath);
            } else if (nodePath.endsWith(FileType.ScriptDecisionTable.toString())) {
                list.add(nodePath);
            } else if (nodePath.endsWith(FileType.DecisionTree.toString())) {
                list.add(nodePath);
            } else if (nodePath.endsWith(FileType.RuleFlow.toString())) {
                list.add(nodePath);
            }
            buildPath(list, node);
        }
    }


    private String getProjects(String path) {
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        int pos = path.indexOf("/");
        return path.substring(0, pos);
    }

    @Override
    public boolean fileExistCheck(String filePath) throws Exception {
        Node rootNode = getRootNode();
        filePath = processPath(filePath);
        if (filePath.contains(" ") || filePath.equals("")) {
            return true;
        }
        if (rootNode.hasNode(filePath)) {
            return true;
        }
        return false;
    }

    @Override
    public RepositoryFile createProject(String projectName, User user, boolean classify) throws Exception {
        if (!permissionService.isAdmin()) {
            throw new NoPermissionException();
        }
        repositoryInteceptor.createProject(projectName);
        Node rootNode = getRootNode();
        if (rootNode.hasNode(projectName)) {
            throw new RuleException("Project [" + projectName + "] already exist.");
        }
        Node projectNode = rootNode.addNode(projectName);
        projectNode.addMixin("mix:versionable");
        projectNode.setProperty(FILE, true);
        projectNode.setProperty(CRATE_USER, user.getUsername());
        projectNode.setProperty(COMPANY_ID, user.getCompanyId());
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        DateValue dateValue = new DateValue(calendar);
        projectNode.setProperty(CRATE_DATE, dateValue);
        session.save();
        createResourcePackageFile(projectName, user);
        createPackageConfigFile(projectName, user);
        createClientConfigFile(projectName, user);
        return buildProjectFile(projectNode, null, classify, null);
    }

    @Override
    public void createDir(String path, User user) throws Exception {
        if (!permissionService.isAdmin()) {
            throw new NoPermissionException();
        }
        repositoryInteceptor.createDir(path);
        Node rootNode = getRootNode();
        path = processPath(path);
        if (rootNode.hasNode(path)) {
            throw new RuleException("Dir [" + path + "] already exist.");
        }
        boolean add = false;
        String[] subpaths = path.split("/");
        Node parentNode = rootNode;
        for (String subpath : subpaths) {
            if (StringUtils.isEmpty(subpath)) {
                continue;
            }
            String subDirs[] = subpath.split("\\.");
            for (String dir : subDirs) {
                if (StringUtils.isEmpty(dir)) {
                    continue;
                }
                if (parentNode.hasNode(dir)) {
                    parentNode = parentNode.getNode(dir);
                } else {
                    parentNode = parentNode.addNode(dir);
                    parentNode.addMixin("mix:versionable");
                    parentNode.addMixin("mix:lockable");
                    parentNode.setProperty(DIR_TAG, true);
                    parentNode.setProperty(FILE, true);
                    parentNode.setProperty(CRATE_USER, user.getUsername());
                    Calendar calendar = Calendar.getInstance();
                    calendar.setTime(new Date());
                    DateValue dateValue = new DateValue(calendar);
                    parentNode.setProperty(CRATE_DATE, dateValue);
                    add = true;
                }
            }
        }
        if (add) {
            session.save();
        }
    }

    @Override
    public void createFile(String path, String content, User user) throws Exception {
        if (!permissionService.isAdmin()) {
            throw new NoPermissionException();
        }
        createFileNode(path, content, user, true);
    }

    @Override
    public boolean fileExist(String path) throws Exception {
        Node rootNode = this.getRootNode();
        return rootNode.hasNode(path);
    }

    @Override
    public void fileRename(String path, String newPath) throws Exception {
        if (!permissionService.isAdmin()) {
            throw new NoPermissionException();
        }

        repositoryInteceptor.renameFile(path, newPath);
        path = processPath(path);
        newPath = processPath(newPath);
        Node rootNode = getRootNode();
        if (!rootNode.hasNode(path)) {
            throw new RuleException("File [" + path + "] not exist.");
        }
        this.refactorService.refactorFile("/" + path, "/" + newPath);
        session.getWorkspace().move("/" + path, "/" + newPath);
        session.save();
    }

    @Override
    public void exportXml(String projectPath, OutputStream outputStream) throws Exception {
        if (!permissionService.isAdmin()) {
            throw new NoPermissionException();
        }
        session.exportSystemView(projectPath, outputStream, false, false);
    }

    @Override
    public void importXml(InputStream inputStream, boolean overwrite) throws Exception {
        if (!permissionService.isAdmin()) {
            throw new NoPermissionException();
        }
        String rootNodePath = getRootNode().getPath();
        if (overwrite) {
            session.importXML(rootNodePath, inputStream, ImportUUIDBehavior.IMPORT_UUID_COLLISION_REPLACE_EXISTING);
        } else {
            session.importXML(rootNodePath, inputStream, ImportUUIDBehavior.IMPORT_UUID_CREATE_NEW);
        }
        session.save();
    }

    private void createResourcePackageFile(String project, User user) throws Exception {
        String filePath = processPath(project) + "/" + RES_PACKGE_FILE;
        Node rootNode = getRootNode();
        if (!rootNode.hasNode(filePath)) {
            createFile(filePath, "<?xml version=\"1.0\" encoding=\"utf-8\"?><res-packages></res-packages>", user);
        }
    }

    private void createPackageConfigFile(String project, User user) throws Exception {
        String filePath = processPath(project) + "/" + PACKAGE_CONFIG_FILE;
        Node rootNode = getRootNode();
        if (!rootNode.hasNode(filePath)) {
            createFile(filePath, "<?xml version=\"1.0\" encoding=\"utf-8\"?><package-config></package-config>", user);
        }
    }

    private void createClientConfigFile(String project, User user) throws Exception {
        Node rootNode = getRootNode();
        String filePath = processPath(project) + "/" + CLIENT_CONFIG_FILE;
        if (!rootNode.hasNode(filePath)) {
            createFile(filePath, "<?xml version=\"1.0\" encoding=\"utf-8\"?><client-config></client-config>", user);
        }
    }

    private void createSecurityConfigFile(User user) throws Exception {
        String companyId = user.getCompanyId();
        String filePath = RESOURCE_SECURITY_CONFIG_FILE + (companyId == null ? "" : companyId);
        Node rootNode = getRootNode();
        if (!rootNode.hasNode(filePath)) {
            createFileNode(filePath, "<?xml version=\"1.0\" encoding=\"utf-8\"?><user-permission></user-permission>", user, false);
        }
    }

    private void createFileNode(String path, String content, User user, boolean isFile) throws Exception {
        String createUser = user.getUsername();
        repositoryInteceptor.createFile(path, content);
        Node rootNode = getRootNode();
        path = processPath(path);
        try {
            if (rootNode.hasNode(path)) {
                throw new RuleException("File [" + path + "] already exist.");
            }
            Node fileNode = rootNode.addNode(path);
            fileNode.addMixin("mix:versionable");
            fileNode.addMixin("mix:lockable");
            Binary fileBinary = new BinaryImpl(content.getBytes());
            fileNode.setProperty(DATA, fileBinary);
            if (isFile) {
                fileNode.setProperty(FILE, true);
            }
            fileNode.setProperty(CRATE_USER, createUser);
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(new Date());
            DateValue dateValue = new DateValue(calendar);
            fileNode.setProperty(CRATE_DATE, dateValue);
            session.save();
        } catch (Exception ex) {
            throw new RuleException(ex);
        }
    }

    private void lockCheck(Node node, User user) throws Exception {
        if (lockManager.isLocked(node.getPath())) {
            String lockOwner = lockManager.getLock(node.getPath()).getLockOwner();
            if (lockOwner.equals(user.getUsername())) {
                return;
            }
            throw new NodeLockException("【" + node.getName() + "】已被" + lockOwner + "锁定!");
        }
    }

    @Autowired
    public void setPermissionService(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        super.setApplicationContext(applicationContext);
        this.refactorService = new RefactorServiceImpl(this, applicationContext);
    }
}
