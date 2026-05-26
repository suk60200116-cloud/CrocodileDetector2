package com.smishing.crocodiledetector

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

class PermissionsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_ob_permissions, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupExpandable(
            view,
            btnId   = R.id.btnExpandMic,
            panelId = R.id.expandMic
        )
        setupExpandable(
            view,
            btnId   = R.id.btnExpandPhone,
            panelId = R.id.expandPhone
        )
        setupExpandable(
            view,
            btnId   = R.id.btnExpandContact,
            panelId = R.id.expandContact
        )
        setupExpandable(
            view,
            btnId   = R.id.btnExpandNotif,
            panelId = R.id.expandNotif
        )
        setupExpandable(
            view,
            btnId   = R.id.btnExpandData,
            panelId = R.id.expandData
        )
    }

    private fun setupExpandable(view: View, btnId: Int, panelId: Int) {
        val btn   = view.findViewById<TextView>(btnId)
        val panel = view.findViewById<View>(panelId)

        btn.setOnClickListener {
            if (panel.visibility == View.GONE) {
                panel.visibility = View.VISIBLE
                btn.text = "∧"
            } else {
                panel.visibility = View.GONE
                btn.text = "∨"
            }
        }
    }
}