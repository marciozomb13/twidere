package org.mariotaku.twidere.loader;

import static org.mariotaku.twidere.util.Utils.getBestCacheDir;
import static org.mariotaku.twidere.util.Utils.getImageLoaderHttpClient;
import static org.mariotaku.twidere.util.Utils.getRedirectedHttpResponse;
import static org.mariotaku.twidere.util.Utils.parseString;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.mariotaku.twidere.Constants;
import org.mariotaku.gallery3d.util.GalleryUtils;
import org.mariotaku.twidere.util.ImageValidator;

import twitter4j.http.HttpClientWrapper;
import twitter4j.http.HttpResponse;
import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.support.v4.content.AsyncTaskLoader;
import com.nostra13.universalimageloader.cache.disc.naming.FileNameGenerator;
import org.mariotaku.twidere.util.URLFileNameGenerator;

public abstract class AbstractImageLoader extends AsyncTaskLoader<AbstractImageLoader.Result> implements Constants {

	private static final String CACHE_DIR_NAME = DIR_NAME_IMAGE_CACHE;

	private final Uri mUri;
	private final Context mContext;
	private final HttpClientWrapper mClient;
	private final Handler mHandler;
	private final DownloadListener mListener;
	private final ContentResolver mResolver;
	private final FileNameGenerator mGenerator;

	protected File mCacheDir, mImageFile;

	public AbstractImageLoader(final Context context, final DownloadListener listener, final Uri uri) {
		super(context);
		mContext = context;
		mHandler = new Handler();
		mUri = uri;
		mClient = getImageLoaderHttpClient(context);
		mListener = listener;
		mResolver = context.getContentResolver();
		mGenerator = new URLFileNameGenerator();
		init();
	}

	@Override
	public AbstractImageLoader.Result loadInBackground() {
		if (mUri == null) return new Result(null, null, null);
		final String scheme = mUri.getScheme();
		if ("http".equals(scheme) || "https".equals(scheme)) {
			final String url = parseString(mUri.toString());
			if (url == null) return new Result(null, null, null);
			if (mCacheDir == null || !mCacheDir.exists()) {
				init();
			}
			final File cache_file = mImageFile = new File(mCacheDir, mGenerator.generate(url));
			try {
				// from SD cache
				if (ImageValidator.checkImageValidity(cache_file)) return decodeImage(cache_file);
				final HttpResponse resp = getRedirectedHttpResponse(mClient, url);
				// from web
				if (resp == null) return null;
				final long length = resp.getContentLength();
				mHandler.post(new DownloadStartRunnable(mListener, length));
				final InputStream is = resp.asStream();
				final OutputStream os = new FileOutputStream(cache_file);
				try {
					dump(is, os);
					mHandler.post(new DownloadFinishRunnable(mListener));
				} finally {
					GalleryUtils.closeSilently(is);
					GalleryUtils.closeSilently(os);
				}
				if (!ImageValidator.checkImageValidity(cache_file)) {
					// The file is corrupted, so we remove it from
					// cache.
					if (cache_file.isFile()) {
						cache_file.delete();
					}
					throw new IOException("Invalid image");
				}
				return decodeImage(cache_file);
			} catch (final Exception e) {
				mHandler.post(new DownloadErrorRunnable(mListener, e));
				return new Result(null, null, e);
			}
		} else if (ContentResolver.SCHEME_FILE.equals(scheme)) {
			mImageFile = new File(mUri.getPath());
			try {
				return decodeImage(mUri);
			} catch (final Exception e) {
				return new Result(null, null, e);
			}
		}
		return new Result(null, null, null);
	}

	protected Result decodeImage(final File file) throws IOException {
		if (ImageValidator.checkImageValidity(file)) return decodeImage(Uri.fromFile(file));
		throw new InvalidImageException();
	}

	protected abstract Result decodeImage(FileDescriptor fd) throws IOException;

	protected Result decodeImage(final Uri uri) throws IOException {
		return decodeImage(mResolver.openFileDescriptor(uri, "r").getFileDescriptor());
	}

	@Override
	protected void onStartLoading() {
		forceLoad();
	}

	private void dump(final InputStream is, final OutputStream os) throws IOException {
		final byte buffer[] = new byte[1024];
		int rc = is.read(buffer, 0, buffer.length);
		long downloaded = 0;
		while (rc > 0) {
			downloaded += rc;
			mHandler.post(new ProgressUpdateRunnable(mListener, downloaded));
			os.write(buffer, 0, rc);
			rc = is.read(buffer, 0, buffer.length);
		}
	}

	private void init() {
		/* Find the dir to save cached images. */
		mCacheDir = getBestCacheDir(mContext, CACHE_DIR_NAME);
		if (mCacheDir != null && !mCacheDir.exists()) {
			mCacheDir.mkdirs();
		}
	}

	public static interface DownloadListener {
		void onDownloadError(Throwable t);

		void onDownloadFinished();

		void onDownloadStart(long total);

		void onProgressUpdate(long downloaded);
	}

	public static class InvalidImageException extends IOException {

		private static final long serialVersionUID = 8996099908714452289L;

	}

	public static class Result {
		public final Bitmap bitmap;
		public final File file;
		public final Exception exception;

		public Result(final Bitmap bitmap, final File file, final Exception exception) {
			this.bitmap = bitmap;
			this.file = file;
			this.exception = exception;
		}
	}

	private final static class DownloadErrorRunnable implements Runnable {

		private final DownloadListener listener;
		private final Throwable t;

		DownloadErrorRunnable(final DownloadListener listener, final Throwable t) {
			this.listener = listener;
			this.t = t;
		}

		@Override
		public void run() {
			if (listener == null) return;
			listener.onDownloadError(t);
		}
	}

	private final static class DownloadFinishRunnable implements Runnable {

		private final DownloadListener listener;

		DownloadFinishRunnable(final DownloadListener listener) {
			this.listener = listener;
		}

		@Override
		public void run() {
			if (listener == null) return;
			listener.onDownloadFinished();
		}
	}

	private final static class DownloadStartRunnable implements Runnable {

		private final DownloadListener listener;
		private final long total;

		DownloadStartRunnable(final DownloadListener listener, final long total) {
			this.listener = listener;
			this.total = total;
		}

		@Override
		public void run() {
			if (listener == null) return;
			listener.onDownloadStart(total);
		}
	}

	private final static class ProgressUpdateRunnable implements Runnable {

		private final DownloadListener listener;
		private final long current;

		ProgressUpdateRunnable(final DownloadListener listener, final long current) {
			this.listener = listener;
			this.current = current;
		}

		@Override
		public void run() {
			if (listener == null) return;
			listener.onProgressUpdate(current);
		}
	}
}
