package com.lukechenshui.jresume;

import com.beust.jcommander.JCommander;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lukechenshui.jresume.resume.Resume;
import com.lukechenshui.jresume.resume.items.Person;
import com.lukechenshui.jresume.resume.items.work.JobWork;
import com.lukechenshui.jresume.resume.items.work.VolunteerWork;
import com.lukechenshui.jresume.themes.BaseTheme;
import com.lukechenshui.jresume.themes.BasicExampleTheme;
import com.lukechenshui.jresume.themes.DefaultTheme;
import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import org.w3c.tidy.Tidy;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Scanner;

import static spark.Spark.*;

public class Main {

    public static void main(String[] args) {
        try {
            registerThemes();
            Config config = new Config();
            new JCommander(config, args);
            //createExample();

            if (Config.serverMode) {
                startListeningAsServer();
            } else {
                generateWebResumeAndWriteIt(null);
            }
        } catch (Exception exc) {
            exc.printStackTrace();
        }

        //createExample();
    }

    private static File generateWebResumeAndWriteIt(String json) throws Exception {
        if (json == null) {
            json = readJSONFromFile();
        }
        String html = generateWebResumeFromJSON(json);
        File location = Runtime.getOutputHtmlFile();
        FileWriter writer = new FileWriter(location, false);
        writer.write(html);
        //System.out.println(html);

        System.out.println("Success! You can find your resume at " + Runtime.getOutputHtmlFile().getAbsolutePath());
        writer.close();
        return location.getParentFile();
    }

    public static void registerThemes() {
        BaseTheme.registerTheme("default", DefaultTheme.class);
        BaseTheme.registerTheme("blankexampletheme", BasicExampleTheme.class);
    }

    public static void createExample(){
        Person person = new Person("John Doe", "Junior Software Engineer",
                "800 Java Road, OOP City", "+1(345)-335-8964", "johndoe@gmail.com",
                "http://johndoe.com");
        JobWork jobWork = new JobWork("Example Ltd.", "Software Engineer",
                "At Example Ltd., I did such and such.");

        jobWork.addHighlight("Worked on such and such");
        jobWork.addHighlight("Also worked on this");
        jobWork.addKeyWord("java");
        jobWork.addKeyWord("c++");

        VolunteerWork volunteerWork = new VolunteerWork("Example Institution", "Volunteer",
                "At Example Institution, I did such and such.");
        Resume resume = new Resume();
        resume.setPerson(person);
        resume.addJobWork(jobWork);
        resume.addVolunteerWork(volunteerWork);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(resume);
        System.out.println(json);
        try(FileWriter writer = new FileWriter("example.json")){
            writer.write(json);
        }
        catch (Exception exc){
            exc.printStackTrace();
            stop();
        }
    }

    private static void copyResourcesZip() throws Exception{
            String classUrl = Main.class.getResource("Main.class").toString();
            File tempFile = File.createTempFile("jresume", "resource");
            tempFile.delete();
            URL url = Main.class.getResource("/resources.zip");
            //System.out.println("JAR Resource Zip URL: " + url.toString());
            InputStream inputStream = url.openStream();
            Files.copy(inputStream, tempFile.toPath());
            Runtime.unzipResourceZip(tempFile.getAbsolutePath());
    }

    private static void startListeningAsServer() throws Exception {
        threadPool(4);
        port(Config.getServerPort());
        post("/webresume", (request, response) -> {

            File location = generateWebResumeAndWriteIt(request.body());
            File outputZipFile = File.createTempFile("jresume", ".tmp");
            if (outputZipFile.exists()) {
                outputZipFile.delete();
            }
            outputZipFile.deleteOnExit();
            ZipFile zipFile = new ZipFile(outputZipFile);
            zipFile.createZipFileFromFolder(location, new ZipParameters(), false, 0);
            HttpServletResponse rawResponse = response.raw();
            rawResponse.setContentType("application/octet-stream");
            rawResponse.setHeader("Content-Disposition", "attachment; filename=resume.zip");
            OutputStream out = rawResponse.getOutputStream();
            out.write(Files.readAllBytes(outputZipFile.toPath()));

            return rawResponse;
        });
        get("/", (request, response) -> {
            return "Welcome to JResume!";
        });

        exception(Exception.class, (exception, request, response) -> {
            exception.printStackTrace();
            stop();
            System.exit(1);
        });
    }

    private static String generateWebResumeFromJSON(String json) throws Exception {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
            copyResourcesZip();
            if (!Files.exists(Paths.get("output"))) {
                Files.createDirectory(Paths.get("output"));
            }


            //System.out.println(json);
            Resume resume = gson.fromJson(json, Resume.class);

            JsonParser parser = new JsonParser();
            JsonObject obj = parser.parse(json).getAsJsonObject();
            resume.setJsonObject(obj);

            BaseTheme theme = Config.getThemeHashMap().get(Config.getThemeName());
            String html = theme.generate(resume);

        html = prettyPrintHTML(html);
        return html;
    }

    private static String prettyPrintHTML(String html) {
        String prettyHTML;
        Tidy tidy = new Tidy();
        tidy.setIndentContent(true);
        tidy.setShowWarnings(false);
        tidy.setQuiet(true);
        tidy.setTrimEmptyElements(false);

        StringReader htmlStringReader = new StringReader(html);
        StringWriter htmlStringWriter = new StringWriter();
        tidy.parseDOM(htmlStringReader, htmlStringWriter);
        prettyHTML = htmlStringWriter.toString();
        return prettyHTML;
    }

    private static String readJSONFromFile() throws Exception {
        String jsonResumePath = Config.getInputFileName();
        String json = "";
        Scanner reader = new Scanner(new File(jsonResumePath));

        while (reader.hasNextLine()) {
            json += reader.nextLine();
            json += "\n";
        }
        reader.close();
        return json;
    }
}
