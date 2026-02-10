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
                System.out.println("Received XML: " + xmlContent);
                
                InputSource source = new InputSource(new StringReader(xmlContent));
                Document doc = DomHelper.parse(source); 
                
                if (doc != null && doc.getDocumentElement() != null) {
                    this.result = "XXE Content: " + doc.getDocumentElement().getTextContent();
                } else {
                    this.result = "Error: Document or Root Element is null.";
                }
            } else {
                this.result = "Please provide 'xmlContent' parameter.";
            }
        } catch (Exception e) {
            e.printStackTrace();
            this.result = "Error: " + e.toString();
        }
        return SUCCESS;
    }

    public String getXmlContent() { return xmlContent; }
    public void setXmlContent(String xmlContent) { this.xmlContent = xmlContent; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
}
