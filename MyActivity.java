//20083224 노창민 입니당

package com.example.chromecastv2;

import java.io.IOException;

import com.google.android.gms.cast.ApplicationMetadata;
import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.Cast.ApplicationConnectionResult;
import com.google.android.gms.cast.Cast.MessageReceivedCallback;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.MediaRouteActionProvider;
import android.support.v7.media.MediaControlIntent;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.support.v7.media.MediaRouter.RouteInfo;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

public class MyActivity extends ActionBarActivity {
	static final String TAG = MyActivity.class.getSimpleName();

	static final int REQUEST_CODE = 1;

	MediaRouter mMediaRouter;
	MediaRouteSelector mMediaRouteSelector;
	MediaRouter.Callback mMediaRouterCallback;
	CastDevice mSelectedDevice;
	GoogleApiClient mApiClient;
	Cast.Listener mCastListener;
	ConnectionCallbacks mConnectionCallbacks;
	ConnectionFailedListener mConnectionFailedListener;
	HelloWorldChannel mHelloWorldChannel;
	boolean mApplicationStarted;
	boolean mWaitingForReconnect;


	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_my);

		mMediaRouter = MediaRouter.getInstance(getApplicationContext());
		mMediaRouteSelector = new MediaRouteSelector.Builder()
		//                .addControlCategory(MediaControlIntent.CATEGORY_LIVE_AUDIO)
		//                .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
		.addControlCategory(CastMediaControlIntent.categoryForCast(getResources()
				.getString(R.string.app_id))).build();
		mMediaRouterCallback = new MyMediaRouterCallback();

	}

	// Add the callback on start to tell the media router what kinds of routes
	// the application is interested in so that it can try to discover suitable ones.
	//    public void onStart() {
	//    	super.onStart();
	//        mRouter.addCallback(mSelector, mediaRouterCallback,
	//                MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);
	//
	//        MediaRouter.RouteInfo route = mRouter.updateSelectedRoute(mSelector);
	//        
	//        // do something with the route...
	//    }
	//
	//    // Remove the selector on stop to tell the media router that it no longer
	//    // needs to invest effort trying to discover routes of these kinds for now.
	//    public void onStop() {
	//        mRouter.removeCallback(mediaRouterCallback);
	//        super.onStop();
	//    }

	@Override
	protected void onResume() {
		// Start media router discovery
		mMediaRouter.addCallback(mMediaRouteSelector, mMediaRouterCallback,
				MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN);
		Log.i(TAG, "cast button");
		super.onResume();
	}

	@Override
	protected void onPause() {
		if (isFinishing()) {
			// End media router discovery
			mMediaRouter.removeCallback(mMediaRouterCallback);
		}
		super.onPause();
	}

	@Override
	public void onDestroy() {
		teardown();
		super.onDestroy();
	}

	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);

		getMenuInflater().inflate(R.menu.sample_media_router_menu, menu);

		MenuItem mediaRouteMenuItem = menu.findItem(R.id.media_route_menu_item);
		MediaRouteActionProvider mediaRouteActionProvider =
				(MediaRouteActionProvider)MenuItemCompat.getActionProvider(mediaRouteMenuItem);
		mediaRouteActionProvider.setRouteSelector(mMediaRouteSelector);
		return true;
	}

	class MyMediaRouterCallback extends MediaRouter.Callback {
		// Implement callback methods as needed.
		@Override
		public void onRouteSelected(MediaRouter router, RouteInfo route) {
			// TODO Auto-generated method stub
			Log.d(TAG, "onRouteSelected");
			mSelectedDevice = CastDevice.getFromBundle(route.getExtras());

			launchReceiver();
		}

		@Override
		public void onRouteUnselected(MediaRouter router, RouteInfo route) {
			// TODO Auto-generated method stub
			Log.d(TAG, "onRouteUnselected: info=" + route);
			teardown();
			mSelectedDevice = null;

		}
	};

	void launchReceiver() {
		try {
			mCastListener = new Cast.Listener() {


				@Override
				public void onApplicationDisconnected(int errorCode) {
					Log.d(TAG, "application has stopped");
					teardown();
				}


			};
			// Connect to Google Play services
			mConnectionCallbacks = new ConnectionCallbacks();
			mConnectionFailedListener = new ConnectionFailedListener();
			Cast.CastOptions.Builder apiOptionsBuilder = Cast.CastOptions
					.builder(mSelectedDevice, mCastListener);
			mApiClient = new GoogleApiClient.Builder(this)
			.addApi(Cast.API, apiOptionsBuilder.build())
			.addConnectionCallbacks(mConnectionCallbacks)
			.addOnConnectionFailedListener(mConnectionFailedListener)
			.build();


			mApiClient.connect();
		} catch (Exception e) {
			Log.e(TAG, "Failed launchReceiver", e);
		}
	}

	private class ConnectionCallbacks implements
	GoogleApiClient.ConnectionCallbacks {
		@Override
		public void onConnected(Bundle connectionHint) {
			Log.d(TAG, "onConnected");


			if (mApiClient == null) {
				// We got disconnected while this runnable was pending
				// execution.
				return;
			}


			try {
				if (mWaitingForReconnect) {
					mWaitingForReconnect = false;


					// Check if the receiver app is still running
					if ((connectionHint != null)
							&& connectionHint
							.getBoolean(Cast.EXTRA_APP_NO_LONGER_RUNNING)) {
						Log.d(TAG, "App  is no longer running");
						teardown();
					} else {
						// Re-create the custom message channel
						try {
							Cast.CastApi.setMessageReceivedCallbacks(
									mApiClient,
									mHelloWorldChannel.getNamespace(),
									mHelloWorldChannel);
						} catch (IOException e) {
							Log.e(TAG, "Exception while creating channel", e);
						}
					}
				} else {
					// Launch the receiver app
					Cast.CastApi
					.launchApplication(mApiClient,
							getString(R.string.app_id), false)
							.setResultCallback(
									new ResultCallback<Cast.ApplicationConnectionResult>() {
										@Override
										public void onResult(
												ApplicationConnectionResult result) {
											Status status = result.getStatus();
											Log.d(TAG,
													"ApplicationConnectionResultCallback.onResult: statusCode"
															+ status.getStatusCode());
											if (status.isSuccess()) {
												ApplicationMetadata applicationMetadata = result
														.getApplicationMetadata();
												String sessionId = result
														.getSessionId();
												String applicationStatus = result
														.getApplicationStatus();
												boolean wasLaunched = result
														.getWasLaunched();
												Log.d(TAG,
														"application name: "
																+ applicationMetadata
																.getName()
																+ ", status: "
																+ applicationStatus
																+ ", sessionId: "
																+ sessionId
																+ ", wasLaunched: "
																+ wasLaunched);
												mApplicationStarted = true;


												// Create the custom message
												// channel
												mHelloWorldChannel = new HelloWorldChannel();
												try {
													Cast.CastApi
													.setMessageReceivedCallbacks(
															mApiClient,
															mHelloWorldChannel
															.getNamespace(),
															mHelloWorldChannel);
												} catch (IOException e) {
													Log.e(TAG,
															"Exception while creating channel",
															e);
												}


												// set the initial instructions
												// on the receiver
												sendMessage(getString(R.string.instructions));
											} else {
												Log.e(TAG,
														"application could not launch");
												teardown();
											}
										}
									});
				}
			} catch (Exception e) {
				Log.e(TAG, "Failed to launch application", e);
			}
		}


		@Override
		public void onConnectionSuspended(int cause) {
			Log.d(TAG, "onConnectionSuspended");
			mWaitingForReconnect = true;
		}
	}


	class HelloWorldChannel implements MessageReceivedCallback {


		/**
		 * @return custom namespace
		 */
		public String getNamespace() {
			return getString(R.string.namespace);
		}


		/*
		 * Receive message from the receiver app
		 */
		@Override
		public void onMessageReceived(CastDevice castDevice, String namespace,
				String message) {
			Log.d(TAG, "onMessageReceived: " + message);
		}
	}

	private void sendMessage(String message) {
		if (mApiClient != null && mHelloWorldChannel != null) {
			try {
				Cast.CastApi.sendMessage(mApiClient,
						mHelloWorldChannel.getNamespace(), message)
						.setResultCallback(new ResultCallback<Status>() {
							@Override
							public void onResult(Status result) {
								if (!result.isSuccess()) {
									Log.e(TAG, "Sending message failed");
								}
							}
						});
			} catch (Exception e) {
				Log.e(TAG, "Exception while sending message", e);
			}
		} else {
			Toast.makeText(MyActivity.this, message, Toast.LENGTH_SHORT)
			.show();
		}
	}


	private class ConnectionFailedListener implements
	GoogleApiClient.OnConnectionFailedListener {
		@Override
		public void onConnectionFailed(ConnectionResult result) {
			Log.e(TAG, "onConnectionFailed ");
			teardown();
		}
	}

	private void teardown() {
		Log.d(TAG, "teardown");
		if (mApiClient != null) {
			if (mApplicationStarted) {
				if (mApiClient.isConnected()) {
					try {
						Cast.CastApi.stopApplication(mApiClient);
						if (mHelloWorldChannel != null) {
							Cast.CastApi.removeMessageReceivedCallbacks(
									mApiClient,
									mHelloWorldChannel.getNamespace());
							mHelloWorldChannel = null;
						}
					} catch (IOException e) {
						Log.e(TAG, "Exception while removing channel", e);
					}
					mApiClient.disconnect();
				}
				mApplicationStarted = false;
			}
			mApiClient = null;
		}
		mSelectedDevice = null;
		mWaitingForReconnect = false;
	}

}
