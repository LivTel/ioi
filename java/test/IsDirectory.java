import java.io.*;
import java.lang.*;
import java.util.*;

public class IsDirectory
{
	public static void main(String[] args)
	{
		File directoryFile = null;
		File directoryList[];
		
		
		if(args.length != 1)
		{
			System.out.println("java IsDirectory <dir string>");
			System.exit(1);
		}
		directoryFile = new File(args[0]);
		if(directoryFile.isDirectory() == true)
		{
			System.out.println(":Specified directory IS a directory:"+directoryFile);
		}
		if(directoryFile.isDirectory() == false)
		{
			System.out.println(":Specified directory IS NOT a directory:"+directoryFile);
		}
		directoryList = directoryFile.listFiles();
		if(directoryList == null)
		{
			System.err.println(":Directory list was null:"+directoryFile);
			System.exit(1);
		}
		for(int i = 0; i < directoryList.length; i++)
		{
			if(directoryList[i].isDirectory())
			{
				System.out.println("Sub directory "+directoryList[i]+" IS a directory.");
			}
		}
		System.exit(0);
	}
}

