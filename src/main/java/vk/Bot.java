package vk;

import com.vk.api.sdk.client.TransportClient;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.client.actors.UserActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import com.vk.api.sdk.objects.docs.Doc;
import com.vk.api.sdk.objects.docs.responses.GetUploadServerResponse;
import com.vk.api.sdk.objects.docs.responses.SaveResponse;
import com.vk.api.sdk.objects.photos.responses.GetWallUploadServerResponse;
import com.vk.api.sdk.objects.photos.responses.SaveWallPhotoResponse;
import com.vk.api.sdk.objects.photos.responses.WallUploadResponse;
import com.vk.api.sdk.objects.wall.GetFilter;
import com.vk.api.sdk.objects.wall.responses.PostResponse;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Bot {
    private final VkApiClient vkClient;
    private final Integer groupId;
    private final GroupActor groupActor;
    private final Integer userId;
    private final UserActor userActor;


    public Bot(Integer groupId, String groupToken, Integer userId, String userToken) {
        this.groupId = groupId;
        this.userId = userId;
        TransportClient transportClient = new HttpTransportClient();
        this.vkClient = new VkApiClient(transportClient);
        this.groupActor = new GroupActor(groupId,groupToken);
        this.userActor = new UserActor(userId,userToken);
    }

    public String getWallUploadURL() throws ClientException, ApiException {
        GetWallUploadServerResponse serverResponse = vkClient.photos().getWallUploadServer(userActor).groupId(groupId).execute();
        return String.valueOf(serverResponse.getUploadUrl());
    }

    public SaveWallPhotoResponse uploadPhotoOnWall(String uploadURL, File file, String photoDescription, Integer intervalMillis) throws ClientException, ApiException, InterruptedException {
        WallUploadResponse response = vkClient.upload().photoWall(uploadURL,file).execute();
        Thread.sleep(intervalMillis);
        List<SaveWallPhotoResponse> photoList = vkClient.photos().saveWallPhoto(userActor,response.getPhoto()).server(response.getServer())
                .hash(response.getHash()).groupId(groupId).caption(photoDescription).execute();
        return photoList.get(0);
    }

    //publish date = 0 to post right now
    public PostResponse postPhotosOnWall(List<File> files, Integer publishDate, String message, String photoDescription, Integer intervalMillis) throws ClientException, ApiException, InterruptedException {
        List<SaveWallPhotoResponse> photoResponses = new ArrayList<>();
        String uploadURL = getWallUploadURL();
        Thread.sleep(intervalMillis);
        for (var file: files) {
            photoResponses.add(uploadPhotoOnWall(uploadURL,file, photoDescription,intervalMillis));
            Thread.sleep(intervalMillis);
        }
        List<String> attachments =  photoResponses.stream().map(x -> "photo" + x.getOwnerId() + "_" + x.getId()).collect(Collectors.toList());
        return vkClient.wall().post(userActor).attachments(attachments).ownerId(-groupId).publishDate(publishDate).message(message).execute();
    }

    public String getDocUploadServer() throws ClientException, ApiException {
        GetUploadServerResponse response = vkClient.docs().getUploadServer(userActor).execute();
        return String.valueOf(response.getUploadUrl());
    }

    public String uploadDoc(String uploadURL,File file) throws ClientException, ApiException {
        return vkClient.upload().doc(uploadURL, file).execute().getFile();
    }

    public SaveResponse saveDoc(String file, String title) throws ClientException, ApiException {
       return vkClient.docs().save(userActor,file).title(title).execute();
    }

    public PostResponse postDocOnwall(File file, String docTitle, Integer publishDate, String message, Integer intervalMillis) throws ClientException, ApiException, InterruptedException {
        String uploadURL = getDocUploadServer();
        Thread.sleep(intervalMillis);
        String fileResponse = uploadDoc(uploadURL,file);
        Thread.sleep(intervalMillis);
        SaveResponse saveResponse = saveDoc(fileResponse,docTitle);
        Doc doc = saveResponse.getDoc();
        String attachment = "doc" + doc.getOwnerId() + "_" + doc.getId();
        return vkClient.wall().post(userActor).ownerId(-groupId).attachments(attachment).message(message).publishDate(publishDate).execute();
    }

    public Integer countPostponed() throws ClientException, ApiException {
        return vkClient.wall().get(userActor).count(100).ownerId(-groupId).filter(GetFilter.POSTPONED).execute().getCount();
    }
}
