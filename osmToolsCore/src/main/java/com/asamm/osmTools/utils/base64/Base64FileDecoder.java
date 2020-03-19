/****************************************************************************
 *
 * Copyright (C) 2009-2010 Menion. All rights reserved.
 *
 * This file is part of the LocA & SmartMaps software.
 *
 * Email menion@asamm.cz for more information.
 *
 * This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
 * WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
 *
 ***************************************************************************/
package com.asamm.osmTools.utils.base64;

import java.io.*;

/**
 * @author menion
 * @since 25.1.2010 2010
 */
public class Base64FileDecoder {

//	public static void main(String args[]) throws IOException {
//		if (args.length != 2) {
//			System.out
//					.println("Command line parameters: inputFileName outputFileName");
//			System.exit(9);
//		}
//		decodeFile(args[0], args[1]);
//	}

	public static void decodeFile(String inputFileName, String outputFileName)
			throws IOException {
		BufferedReader in = null;
		BufferedOutputStream out = null;
		try {
			in = new BufferedReader(new FileReader(inputFileName));
			out = new BufferedOutputStream(new FileOutputStream(outputFileName), 10240);
			decodeStream(in, out);
			out.flush();
		} finally {
			if (in != null)
				in.close();
			if (out != null)
				out.close();
		}
	}

	private static void decodeStream(BufferedReader in, OutputStream out)
			throws IOException {
		while (true) {
			String s = in.readLine();
			if (s == null)
				break;
			byte[] buf = Base64Coder.decode(s);
			out.write(buf);
		}
	}

} // end class Base64FileDecoder
