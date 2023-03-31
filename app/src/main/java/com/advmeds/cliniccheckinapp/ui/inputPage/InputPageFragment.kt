package com.advmeds.cliniccheckinapp.ui.inputPage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.text.InputType
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.fragment.findNavController
import coil.load
import com.advmeds.cliniccheckinapp.R
import com.advmeds.cliniccheckinapp.databinding.InputPageFragmentBinding
import com.advmeds.cliniccheckinapp.ui.MainActivity
import com.advmeds.cliniccheckinapp.ui.MainViewModel
import com.advmeds.cliniccheckinapp.utils.NationIdTransformationMethod
import com.advmeds.cliniccheckinapp.utils.showOnly
import okhttp3.HttpUrl
import timber.log.Timber

class InputPageFragment : Fragment() {
    companion object {
        const val RELOAD_CLINIC_LOGO_ACTION = "reload_clinic_logo_action"
        const val CLINIC_LOGO_URL_KEY = "clinic_logo_url"
    }

    private val viewModel: InputPageViewModel by viewModels()
    private val activityViewModel: MainViewModel by activityViewModels()

    private var _binding: InputPageFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private var handler: Handler? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = InputPageFragmentBinding.inflate(inflater, container, false)

        binding.logoImageView.load(viewModel.logo)

        setUpHandler()

        return binding.root
    }

    private fun setUpHandler() {
        handler = Handler()

        // Post a delayed action to navigate to the home fragment after 30 seconds
        setTimer()

        // Add a touch listener to reset the handler when the view is touched
        binding.root.setOnClickListener {
            resetHandler()
        }
    }

    private fun setTimer() {
        handler?.postDelayed({
            goToHomePage()
        }, 30000)
    }

    private fun resetHandler() {
        handler?.removeCallbacksAndMessages(null)
        setTimer()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
    }

    private fun setupUI() {
        //binding.checkInLayout.visibility =
        //    if (BuildConfig.PRINT_ENABLED) View.VISIBLE else View.GONE


//        binding.checkInButton.setOnClickListener {
//            val activity = requireActivity() as MainActivity
//            activity.dialog?.dismiss()
//            activity.dialog = CheckInDialogFragment()
//            activity.dialog?.showNow(parentFragmentManager, null)
//        }

        binding.backToHomePageButton.setOnClickListener {
            goToHomePage()
        }

        val arg = getString(R.string.national_id)
        val text = String.format(getString(R.string.national_id_input_title), arg)
        val textColor = ContextCompat.getColor(
            requireContext(),
            R.color.colorPrimary
        )
        val textStart = text.indexOf(arg)
        val textEnd = textStart + arg.length
        val spannable = SpannableString(text)
        spannable.setSpan(
            ForegroundColorSpan(textColor),
            textStart,
            textEnd,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        binding.idInputTitleTv.text = spannable
        binding.idInputEt.hint = String.format(getString(R.string.national_id_input_hint), arg)
        binding.idInputEt.transformationMethod = NationIdTransformationMethod()

        setupKeyboard()
    }

    private fun goToHomePage() {
        val action = InputPageFragmentDirections.actionInputPageFragmentToHomeFragment()
        findNavController().navigate(action)
    }

    private fun setupKeyboard() {
        val onKeyClicked = View.OnClickListener {
            val currentText = binding.idInputEt.text.toString()
            val key = (it as Button).text.toString()

            resetHandler()

            binding.idInputEt.setText(currentText + key)
        }

        binding.enPadLayout.children.forEach { children ->
            when (children) {
                is ViewGroup -> {
                    children.children.forEach {
                        if (it is Button) {
                            it.setOnClickListener(onKeyClicked)
                        }
                    }
                }
                is Button -> {
                    children.setOnClickListener(onKeyClicked)
                }
            }
        }

        binding.numberPadLayout.children.forEach { children ->
            when (children) {
                is ViewGroup -> {
                    children.children.forEach {
                        if (it is Button) {
                            it.setOnClickListener(onKeyClicked)
                        }
                    }
                }
                is Button -> {
                    children.setOnClickListener(onKeyClicked)
                }
            }
        }

        binding.backspaceButton.setOnClickListener {
            val currentText = binding.idInputEt.text.toString()

            resetHandler()

            binding.idInputEt.setText(currentText.dropLast(1))
        }

        binding.enterButton.setOnClickListener {
            val patient = binding.idInputEt.text.toString().trim()
            val result = patientValidCheck(patient)

            if (result) {
                (requireActivity() as MainActivity).getPatients(patient) {
                    binding.idInputEt.text = null
                }
            }
            else {
                (requireActivity() as MainActivity).showNoValidIdErrorDialog (
                    title = getString(R.string.id_is_invalid_title),
                    message = getString(R.string.id_is_invalid_message)
                )
                binding.idInputEt.text = null
            }
        }
    }

    private fun patientValidCheck(patient: String): Boolean {
        val regex = Regex("[A-Z][12]\\d{8}")
        return patient.length == 10 && regex.matches(patient)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler?.removeCallbacksAndMessages(null)
        _binding = null
    }

}