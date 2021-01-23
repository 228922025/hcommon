package com.hhh.common.msg;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.simplejavamail.outlookmessageparser.OutlookMessageParser;
import org.simplejavamail.outlookmessageparser.model.OutlookAttachment;
import org.simplejavamail.outlookmessageparser.model.OutlookFileAttachment;
import org.simplejavamail.outlookmessageparser.model.OutlookMessage;
import org.simplejavamail.outlookmessageparser.model.OutlookMsgAttachment;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

public class MsgUtil {
    /**
     * 解析MSG邮件，可以将邮件以HTML展示。
     *
     * @return vo
     * @throws IOException IO异常
     */

    private static OutlookMessage parseMsgFile(String msgPath)
            throws IOException {
        InputStream resourceAsStream = OutlookMessageParser.class.getClassLoader().getResourceAsStream(msgPath);
        return new OutlookMessageParser().parseMsg(resourceAsStream);
    }


    public static String getSuffix(String fileName) {
        if (fileName.contains(".")) {
            String suffix = fileName.substring(fileName.lastIndexOf("."));
            return suffix.toLowerCase();
        }
        return "";
    }

    public static File getTmpDir() {
        String projectPath = System.getProperty("user.dir") + File.separator + "temp";
        File file = new File(projectPath);
        if (!file.exists()) {
            file.mkdirs();
        }
        return file;

    }

    public static void main(String[] args) throws IOException {
        //String path="e:\\用户配置文件勿删\\yusy02\\Desktop\\GIS见费出单Email Template\\Email Template\\Agent Letter.msg";
        //String path="e:\\用户配置文件勿删\\yusy02\\Desktop\\GIS见费出单Email Template\\Email Template\\embedded image.msg";
        //String path="e:\\用户配置文件勿删\\yusy02\\Desktop\\GIS见费出单Email Template\\Email Template\\chinese message.msg";
        //String path = "e:\\用户配置文件勿删\\yusy02\\Desktop\\GIS见费出单Email Template\\Email Template\\Policy Documents - 0078877RA2020.msg";
        String path = "e:\\用户配置文件勿删\\yusy02\\Desktop\\GIS见费出单Email Template\\Email Template\\forward with attachments and embedded images.msg";
        //String path = "e:\\用户配置文件勿删\\yusy02\\Desktop\\GIS见费出单Email Template\\Email Template\\Policy Documents - 0078877RA2020.msg";
        //String path = "e:\\用户配置文件勿删\\yusy02\\Desktop\\GIS见费出单Email Template\\Email Template\\S_MIME test message signedencrypted.msg";
        EmailPreviewVO emailPreviewVo = msgParseToPreview(new File(path));
        writeHtmlFile(emailPreviewVo);
    }

 /*   public static File createTmpFile(String suffix) {
        return new File(getTmpDir(), UUID.randomUUID().toString().replace("-", "") + suffix);
    }*/

    public static File createTmpFileWithName(String fileName) throws IOException {
        File file = new File(getTmpDir(), fileName);
        if (!file.exists()) {
            file.createNewFile();
        }
        return file;
    }

    /**
     * 生存html文件
     *
     * @param email
     */
    public static String writeHtmlFile(EmailPreviewVO email) throws IOException {
        String name = email.getFileName();
        name = name.replace(getSuffix(name), ".html");
        File file = createTmpFileWithName(name);
        String cont = "发送时间:" + email.getSentDate() + "</br>" +
                "发件人:" + email.getFrom() + "</br>" +
                "抄送:" + email.getCc() + "</br>" +
                "收件人:" + email.getTo() + "</br>" +
                "主题:" + email.getSubject() + "</br>"+
                "附件:" + email.getAttachments() + "</br>"+
                email.getContent();
        Files.write(file.toPath(), cont.getBytes(), StandardOpenOption.TRUNCATE_EXISTING);
        return file.getAbsolutePath();
    }

    public static EmailPreviewVO msgParseToPreview(File file) throws IOException {

        EmailPreviewVO vo = new EmailPreviewVO();
        vo.setFileName(file.getName());
        OutlookMessageParser msgp = new OutlookMessageParser();
        OutlookMessage msg = msgp.parseMsg(file.getAbsolutePath());

        List<FileVO> attachList = new ArrayList<FileVO>();
        List<OutlookAttachment> outlookAttachments = msg.getOutlookAttachments();
        for (OutlookAttachment outlookattachment : outlookAttachments) {
            /** TODO 注意：OutlookAttachment 是个接口有两个实现类，
             *  1)、OutlookFileAttachment  存在真实文件字节数据集
             *  2)、OutlookMsgAttachment 为.msg格式文件再次被递归解析
             *      目前没有好办法去获取到邮件附件为.msg格式真实文件，
             */
            // .msg格式附件暂时忽略
            if (outlookattachment instanceof OutlookMsgAttachment) {
                continue;
            }
            OutlookFileAttachment attachment = (OutlookFileAttachment) outlookattachment;
            String attachName = attachment.getFilename();
            if (StringUtils.isBlank(attachName)) {
                attachName = attachment.getLongFilename();
            }
            //存在没有命名的文件
            if (StringUtils.isBlank(attachName)){
                attachName= UUID.randomUUID().toString().replace("-", "");
            }
            File attachementFile = null;
            String suffix = getSuffix(attachName);
            //创建临时文件
            attachementFile = createTmpFileWithName(attachName);

            InputStream is = new ByteArrayInputStream(attachment.getData());
            Files.copy(is, attachementFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            if (attachementFile != null) {
                FileVO fileVo = new FileVO();
                fileVo.setFileName(attachName);
                //这个可能为空
                fileVo.setSuffix(suffix);
                //好像除了png图片外,其他的ContentId是空的
                fileVo.setDesc(attachment.getContentId());
                fileVo.setFileLength(attachementFile.length());
                fileVo.setFilePath(attachementFile.getAbsolutePath());
                attachList.add(fileVo);
            }
        }
        vo.setAttachments(attachList);

        // 内容 要处理下不然他会带有微软雅黑的样式，与原邮件样式不符

        String bodyText = msg.getBodyText();
        //防止空指针
        if (bodyText!=null) {
            Document doc = Jsoup.parse(msg.getConvertedBodyHTML());
            List<FileVO> newAttachList = new ArrayList<FileVO>();
            newAttachList.addAll(attachList);

            // 对邮件中图片进行处理,这里的处理方式是把附件进行转码.然后在页面展示处理
            Elements imgList = doc.select("img");
            for (Element element : imgList) {
                String src = element.attr("src");
                if (src.indexOf("cid:") < 0) {
                    continue;
                }
                String imgAttach = src.substring(4);
                FileVO fileVo = null;
                for (FileVO tmp : attachList) {
                    if (imgAttach.contains(tmp.getFileName())) {
                        fileVo = tmp;
                        break;
                    }
                       /* if (tmp.getDescription().equals(imgAttach)) {
                            fileVo = tmp;
                            break;
                        }*/
                }
                if (fileVo == null) {
                    continue;
                }
                File attach = new File(fileVo.getFilePath());
                String base64 = null;
                InputStream in = null;
                try {
                    in = new FileInputStream(attach);
                    byte[] bytes = new byte[(int) attach.length()];
                    in.read(bytes);
                    base64 = Base64.getEncoder().encodeToString(bytes);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (in != null) {
                        try {
                            in.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }

                if (StringUtils.isNotBlank(base64)) {
                    String srcBase64 = "data:image/png;base64," + base64;
                    element.attr("src", srcBase64);
                    if (newAttachList != null && newAttachList.size() > 0 && newAttachList.contains(fileVo)) {
                        newAttachList.remove(fileVo);
                    }
                }
            }
            // 内容
            Elements bodyList = doc.select("body");
            if (bodyList.size() > 0) {
                Element bodyEle = bodyList.first();
                if (bodyEle.html().length() > 0) {
                    vo.setContent(bodyEle.html());
                }
            }
        }else{
            vo.setContent("");
        }

        // 消息头信息
        if (msg.getClientSubmitTime() != null) {
            vo.setSentDate(msg.getClientSubmitTime().toLocaleString());// 日期格式化，自己手动处理下
        }
        vo.setFrom(msg.getFromEmail());
        vo.setTo(msg.getDisplayTo().trim());
        vo.setCc(msg.getDisplayCc().trim());
        vo.setSubject(msg.getSubject());
        return vo;
    }
}
