package mx.ipn.cic.piig.appproyectomapa


import android.Manifest

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMap.OnMyLocationButtonClickListener
import com.google.android.gms.maps.GoogleMap.OnMyLocationClickListener
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.database.*
import mx.ipn.cic.piig.appproyectomapa.PermissionUtils.PermissionDeniedDialog.Companion.newInstance
import mx.ipn.cic.piig.appproyectomapa.PermissionUtils.isPermissionGranted
import mx.ipn.cic.piig.appproyectomapa.PermissionUtils.requestPermission
import mx.ipn.cic.piig.appproyectomapa.databinding.ActivityMapsBinding


class MapsActivity : AppCompatActivity(), OnMyLocationButtonClickListener,
    OnMyLocationClickListener, OnMapReadyCallback, ActivityCompat.OnRequestPermissionsResultCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding
    private var permissionDenied = false
    private var locationPermissionGranted = false
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var lastKnownLocation: Location
    private var PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 0
    private lateinit var referenciaBD: DatabaseReference
    private lateinit var visitas: ArrayList<Coordenadas>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        visitas = arrayListOf()
        val database : FirebaseDatabase = FirebaseDatabase.getInstance()
        referenciaBD = database.getReference("app_mapas/Coordenadas")

        referenciaBD.addChildEventListener(object : ChildEventListener {
            override fun onCancelled(databaseError: DatabaseError) {}
            override fun onChildMoved(dataSnapshot: DataSnapshot, previousName: String?) {}
            override fun onChildChanged(dataSnapshot: DataSnapshot, previousName: String?) {
                Log.d("onChildChanged", dataSnapshot.toString())
            }
            override fun onChildRemoved(dataSnapshot: DataSnapshot) {
                Log.d("onChildRemoved", dataSnapshot.toString())

            }

            override fun onChildAdded(dataSnapshot: DataSnapshot, s: String?) {
                Log.d("onChildAdded", dataSnapshot.toString())
                Log.d("onChildAdded2", dataSnapshot.key.toString())
                var coordenadas = dataSnapshot.getValue(Coordenadas::class.java)!!
                visitas.add(coordenadas)
                showMarkers(visitas)
                //adapter.notifyDataSetChanged()
            }
        })


        // Botòn para centrar la vista del mapa
        val locationButton: ImageView = mapFragment.requireView().findViewWithTag("GoogleMapMyLocationButton") as ImageView

        // Provider client para obtener ubicación
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        // Listener del botòn de guardado
        binding.btnGuardar.setOnClickListener {
            // Salva ubicación
            getDeviceLocation()
            if(mMap != null)
            {
                if(locationButton != null){ locationButton.callOnClick()}
            }

        }
        if(locationButton != null){ locationButton.callOnClick()}

        // Listener del botón de borrar
        binding.btnBorrar.setOnClickListener {
            referenciaBD.setValue(null)
            visitas = arrayListOf()
            mMap.clear()
            Toast.makeText(this, "Datos eliminados correctamente", Toast.LENGTH_LONG).show()
        }


    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // Add a marker in Mexico City and move the camera
        val cdmx = LatLng(19.432608, -99.133209)
        //mMap.addMarker(MarkerOptions().position(cdmx).title("Ciudad de México"))
        mMap.moveCamera(CameraUpdateFactory.newLatLng(cdmx))
        mMap.moveCamera(CameraUpdateFactory.zoomTo(15F))


        // Modificar propiedades en tiempo de ejecución.
        mMap.mapType = GoogleMap.MAP_TYPE_NORMAL

        mMap.setOnMyLocationButtonClickListener(this)
        mMap.setOnMyLocationClickListener(this)
        enableMyLocation()

    }

    // https://developers.google.com/maps/documentation/android-sdk/location
    // https://github.com/googlemaps/android-samples/blob/master/ApiDemos/kotlin/app/src/gms/java/com/example/kotlindemos/MyLocationDemoActivity.kt

    /**
     * Enables the My Location layer if the fine location permission has been granted.
     */
    @SuppressLint("MissingPermission")
    private fun enableMyLocation() {
        // Comprobar que la referencia al mapa googleMap es válida.
        if (!::mMap.isInitialized) return
        // [START maps_check_location_permission]

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.isMyLocationEnabled = true
        } else {
            // Permission to access the location is missing. Show rationale and request permission
            requestPermission(this, LOCATION_PERMISSION_REQUEST_CODE,
                Manifest.permission.ACCESS_FINE_LOCATION, true
            )
        }
        getLocationPermission()
        // [END maps_check_location_permission]
    }

    override fun onMyLocationButtonClick(): Boolean {
        // Toast.makeText(this, "Ha presionado el botón de Posición Actual", Toast.LENGTH_SHORT).show()
        // Return false so that we don't consume the event and the default behavior still occurs
        // (the camera animates to the user's current position).
        return false
    }

    override fun onMyLocationClick(location: Location) {
        // Obtener las coordenadas de latitud y longitud.
        val latitud = location.latitude
        val longitud = location.longitude

        Toast.makeText(this, "Posición Actual:\n($latitud, $longitud)", Toast.LENGTH_LONG).show()
    }

    // Permiso para acceder a la ubicación del dispositivo
    private fun getLocationPermission() {
        /*
         * Request location permission, so that we can get the location of the
         * device. The result of the permission request is handled by a callback,
         * onRequestPermissionsResult.
         */
        if (ContextCompat.checkSelfPermission(this.applicationContext,
                Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            locationPermissionGranted = true
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION)
        }
    }
    // [START maps_check_location_permission_result]
    @SuppressLint("MissingSuperCall")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode != LOCATION_PERMISSION_REQUEST_CODE) {
            return
        }
        locationPermissionGranted = false
        when (requestCode) {
            PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION -> {

                // If request is cancelled, the result arrays are empty.
                if (grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    locationPermissionGranted = true
                }
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
        if (isPermissionGranted(permissions, grantResults, Manifest.permission.ACCESS_FINE_LOCATION)) {
            // Enable the my location layer if the permission has been granted.
            Toast.makeText(this, "Permiso concedido", Toast.LENGTH_LONG).show()
            enableMyLocation()
        } else {
            // Permission was denied. Display an error message
            // [START_EXCLUDE]
            // Display the missing permission error dialog when the fragments resume.
            permissionDenied = true
            // [END_EXCLUDE]
        }
    }


    // Método para acceder a la ubicación del dispositivo
    @SuppressLint("MissingPermission")
    private fun getDeviceLocation() {
        /*
         * Get the best and most recent location of the device, which may be null in rare
         * cases when a location is not available.
         */

        val locationResult = fusedLocationProviderClient.lastLocation

        locationResult.addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                // Manda guardar la ubicación del dispoitivo
                lastKnownLocation = task.result
                var lat = lastKnownLocation.latitude
                var lng = lastKnownLocation.longitude
                Toast.makeText(this, "Guardando posición actual: ($lat,$lng)", Toast.LENGTH_SHORT).show()
                saveMarker(lat,lng)

                Log.d("Guardar", "Se Registrò: "+lastKnownLocation.toString())


            } else {
                Log.d("error", "Current location is null. Using defaults.")
                Log.e("error", "Exception: %s", task.exception)

            }
        }


    }

    private fun saveMarker(lat: Double = 0.0, lng: Double = 0.0){
        var coordenadas = Coordenadas(lat,lng)
        referenciaBD.push().setValue(coordenadas)

    }

    private fun showMarkers(visitas: ArrayList<Coordenadas>){
        for (v in visitas) {
            mMap.addMarker(
                MarkerOptions().position(LatLng(v.lat,v.lng)).title("Visita")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)))
        }

    }

    // [END maps_check_location_permission_result]
    override fun onResumeFragments() {
        super.onResumeFragments()
        if (permissionDenied) {
            // Permission was not granted, display error dialog.
            showMissingPermissionError()
            permissionDenied = false
        }
    }

    /**
     * Displays a dialog with error message explaining that the location permission is missing.
     */
    private fun showMissingPermissionError() {
        newInstance(true).show(supportFragmentManager, "dialog")
    }



    companion object {
        /**
         * Request code for location permission request.
         *
         * @see .onRequestPermissionsResult
         */
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
    }
}







/*
class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // Add a marker in Sydney and move the camera
        val cdmx = LatLng(19.432608, -99.133209)
        //mMap.addMarker(MarkerOptions().position(cdmx).title("Ciudad de México"))
        mMap.moveCamera(CameraUpdateFactory.newLatLng(cdmx))
        mMap.moveCamera(CameraUpdateFactory.zoomTo(12F))


    }
}*/