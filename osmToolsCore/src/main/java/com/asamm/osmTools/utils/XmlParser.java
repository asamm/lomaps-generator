package com.asamm.osmTools.utils;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.*;

public abstract class XmlParser {

	private XmlPullParser mParser;
	
	public XmlParser(File file) throws IOException, XmlPullParserException {
        // check file for BOM header
        FileInputStream fis = new FileInputStream(file);
        byte[] bomHeader = new byte[3];
        fis.read(bomHeader);
        if (bomHeader[0] == (byte) 239 && bomHeader[1] == (byte) 187 && bomHeader[2] == (byte) 191) {
        	// BOM header is here, need to be removed
        } else {
        	IOUtils.closeQuietly(fis);
        	fis = new FileInputStream(file);
        }

        // get encoding
        byte[] data = FileUtils.readFileToByteArray(file);
        String encoding = Utils.getEncoding(new String(data));
        
        // initialize parser
        mParser = XmlPullParserFactory.newInstance().newPullParser();
        mParser.setInput(fis, encoding);
	}
	
	public XmlParser(byte[] data) throws XmlPullParserException {
		this(new ByteArrayInputStream(data));
	}
	
	public XmlParser(InputStream is) throws XmlPullParserException {
        mParser = XmlPullParserFactory.newInstance().newPullParser();
        mParser.setInput(new InputStreamReader(is));
	}
	
	public XmlParser(String data) throws XmlPullParserException {
        mParser = XmlPullParserFactory.newInstance().newPullParser();
        mParser.setInput(new StringReader(data));
	}
	
	public void parse() throws Exception {
		int event;
		String tagName;

		boolean breaked = false;
		
		while (true) {
			event = mParser.nextToken();
			if (event == XmlPullParser.START_TAG) {
				tagName = mParser.getName();
				if (!tagStart(mParser, tagName)) {
					breaked = true;
					break;
				}
			} else if (event == XmlPullParser.END_TAG) {
				tagName = mParser.getName();
				if (!tagEnd(mParser, tagName)) {
					breaked = true;
					break;
				}
			} else if (event == XmlPullParser.END_DOCUMENT) {
				break;
			}
		}
		// notify about finished task
		parsingFinished(!breaked);
	}

	public abstract boolean tagStart(XmlPullParser parser, String tagName)
			throws Exception;
	
	public abstract boolean tagEnd(XmlPullParser parser, String tagName)
			throws Exception;
	
	public abstract void parsingFinished(boolean success);
}
