package fr.free.nrw.commons.depictions.Media;

import static fr.free.nrw.commons.di.CommonsApplicationModule.IO_THREAD;
import static fr.free.nrw.commons.di.CommonsApplicationModule.MAIN_THREAD;

import android.annotation.SuppressLint;
import fr.free.nrw.commons.Media;
import fr.free.nrw.commons.kvstore.JsonKvStore;
import fr.free.nrw.commons.media.MediaClient;
import io.reactivex.Scheduler;
import io.reactivex.disposables.CompositeDisposable;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Named;
import timber.log.Timber;

/**
 * Presenter for DepictedImagesFragment
 */
public class DepictedImagesPresenter implements DepictedImagesContract.UserActionListener {

    private static final DepictedImagesContract.View DUMMY = (DepictedImagesContract.View) Proxy
            .newProxyInstance(
                    DepictedImagesContract.View.class.getClassLoader(),
                    new Class[]{DepictedImagesContract.View.class},
                    (proxy, method, methodArgs) -> null);
    MediaClient mediaClient;
    @Named("default_preferences")
    JsonKvStore depictionKvStore;
    private final Scheduler ioScheduler;
    private final Scheduler mainThreadScheduler;
    private DepictedImagesContract.View view = DUMMY;
    private CompositeDisposable compositeDisposable = new CompositeDisposable();
    /**
     * Wikibase enitityId for the depicted Item
     * Ex: Q9394
     */
    private List<Media> queryList = new ArrayList<>();

    @Inject
    public DepictedImagesPresenter(@Named("default_preferences") JsonKvStore depictionKvStore,
        MediaClient mediaClient,
        @Named(IO_THREAD) Scheduler ioScheduler,
        @Named(MAIN_THREAD) Scheduler mainThreadScheduler) {
        this.depictionKvStore = depictionKvStore;
        this.ioScheduler = ioScheduler;
        this.mainThreadScheduler = mainThreadScheduler;
        this.mediaClient = mediaClient;
    }

    @Override
    public void onAttachView(DepictedImagesContract.View view) {
        this.view = view;
    }

    @Override
    public void onDetachView() {
        this.view = DUMMY;
    }

    /**
     * Checks for internet connection and then initializes the grid view with first 10 images of that depiction
     */
    @SuppressLint("CheckResult")
    @Override
    public void initList(String entityId) {
        view.setLoadingStatus(true);
        view.progressBarVisible(true);
        view.setIsLastPage(false);
        compositeDisposable.add(mediaClient.fetchImagesForDepictedItem(entityId, 0)
                .subscribeOn(ioScheduler)
                .observeOn(mainThreadScheduler)
                .subscribe(this::handleSuccess, this::handleError));
    }

    /**
     * Fetches more images for the item and adds it to the grid view adapter
     * @param entityId
     */
    @SuppressLint("CheckResult")
    @Override
    public void fetchMoreImages(String entityId) {
        view.progressBarVisible(true);
        compositeDisposable.add(mediaClient.fetchImagesForDepictedItem(entityId, queryList.size())
                .subscribeOn(ioScheduler)
                .observeOn(mainThreadScheduler)
                .subscribe(this::handlePaginationSuccess, this::handleError));
    }

    /**
     * Handles the success scenario
     * it initializes the recycler view by adding items to the adapter
     */
    private void handlePaginationSuccess(List<Media> media) {
        queryList.addAll(media);
        view.progressBarVisible(false);
        view.addItemsToAdapter(media);
    }

    /**
     * Logs and handles API error scenario
     *
     * @param throwable
     */
    public void handleError(Throwable throwable) {
        Timber.e(throwable, "Error occurred while loading images inside items");
        try {
            view.initErrorView();
            view.showSnackBar();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * Handles the success scenario
     * On first load, it initializes the grid view. On subsequent loads, it adds items to the adapter
     * @param collection List of new Media to be displayed
     */
    public void handleSuccess(List<Media> collection) {
        if (collection == null || collection.isEmpty()) {
            if (queryList.isEmpty()) {
                view.initErrorView();
            } else {
                view.setIsLastPage(true);
            }
        } else {
            this.queryList.addAll(collection);
            view.handleSuccess(collection);
        }
    }


    /**
     * add items to query list
     */
    @Override
    public void addItemsToQueryList(List<Media> collection) {
        queryList.addAll(collection);
    }
}