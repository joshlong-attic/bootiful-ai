package com.example.multimodal;

import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.messages.Media;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.image.ImageClient;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.util.FileCopyUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.stream.Stream;

import static org.springframework.util.MimeTypeUtils.IMAGE_PNG;

@SpringBootApplication
public class MultimodalApplication {

    public static void main(String[] args) {
        SpringApplication.run(MultimodalApplication.class, args);
    }

    private static final File DESKTOP = new File(System.getProperty("user.home"), "Desktop");


    //    @Bean
    ApplicationRunner chatClientDemo(ChatClient cc) {
        return args -> {
            var response = cc.call("""
                        Dear Singularity,
                        
                        Please tell me a story about the wonderful Java and Spring developers in Amsterdam, 
                        and do so in the style of famed children's author Dr. Seuss.
                        
                        Cordially,
                        Josh
                    """);
            System.out.println("response: " + response);
        };
    }


    @Bean
    ApplicationRunner imageClientDemo(ImageClient imageClient) {
        return args -> {


            var reply = imageClient
                    .call(new ImagePrompt(
                            """ 
                                    please render a picture showing an amazing Spring and Java developer in the city of Amsterdam, and
                                    be sure to put a Spring Framework leaf on her laptop and not an Apple 'apple' logo. 
                                    """
                    ));
            var response = new UrlResource(reply.getResult().getOutput().getUrl());
            var bytes = response.getContentAsByteArray();
            FileCopyUtils.copy(bytes, new FileOutputStream(new File(DESKTOP, "output.png")));

        };
    }

    //    @Bean
    ApplicationRunner multimodal(ChatClient chatClient) {
        return args -> {
            var options = OpenAiChatOptions
                    .builder()
                    .withModel(OpenAiApi.ChatModel.GPT_4_O.getValue())
                    .build();
            var media = new Media(IMAGE_PNG, new UrlResource("https://shorturl.at/kwEQ6")
                    .getContentAsByteArray());
            var response = chatClient.call(new Prompt(
                    new UserMessage("what's in the following image?", media), options));
            var content = response.getResult().getOutput().getContent();
            System.out.println("content: " + content);
        };
    }


    @Bean
    TokenTextSplitter tokenTextSplitter() {
        return new TokenTextSplitter();
    }

    private static void init(VectorStore vectorStore, TokenTextSplitter tokenTextSplitter,
                             JdbcClient template, Resource pdfResource) {

        template.sql("delete from vector_store").update();

        var config = PdfDocumentReaderConfig
                .builder()
                .withPageExtractedTextFormatter(new ExtractedTextFormatter.Builder().withNumberOfBottomTextLinesToDelete(3)
                        .withNumberOfTopPagesToSkipBeforeDelete(1)
                        .build())
                .withPagesPerDocument(1)
                .build();

        var pdfReader = new PagePdfDocumentReader(pdfResource, config);
        vectorStore.accept(tokenTextSplitter.apply(pdfReader.get()));

    }

    @Bean
    ApplicationRunner rag(TokenTextSplitter tokenTextSplitter,
                          VectorStore vectorStore,
                          JdbcClient jdbcClient,
                          @Value("file://${HOME}/Desktop/carina.pdf") Resource pdf ,
                          ChatClient chatClient) {
        return args -> {
            init(vectorStore, tokenTextSplitter, jdbcClient, pdf);
            var response = chatClient.call(
                "what should I know about the transition to consumer direct care network washington?");
            System.out.println("response: " +response);
        };

    }


    // first run  ` pdftoppm -png  pdf-1.pdf page `
    //@Bean
    ApplicationRunner summarizeAPdf(ChatClient chatClient) {
        return args -> {
            var options = OpenAiChatOptions
                    .builder()
                    .withModel(OpenAiApi.ChatModel.GPT_4_O.getValue())
                    .build();

            var samples = new File(new File(System.getenv("HOME"), "Desktop"), "samples");
            var pdfPngs = Stream
                    .of(Objects.requireNonNull(samples.listFiles((dir, name) -> name.startsWith("page-") && name.endsWith(".png"))))
                    .map(file -> {
                        try {
                            return new Media(IMAGE_PNG, new FileSystemResource(file).getContentAsByteArray());
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
            var response = chatClient.call(new Prompt(new UserMessage("what do the attached documents talk about? ", pdfPngs.toList()), options));
            System.out.println(response);


        };

    }


}
