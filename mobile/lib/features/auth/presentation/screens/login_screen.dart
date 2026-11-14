import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../../core/theme/app_spacing.dart';
import '../../../../shared/widgets/app_button.dart';
import '../../../../shared/widgets/app_text_field.dart';
import '../../../../shared/widgets/app_components.dart';
import '../notifiers/auth_notifier.dart';

class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen>
    with TickerProviderStateMixin {

  // Vista activa
  bool _isBarberiaTab = true;   // Barbería / Cliente
  bool _isLoginMode = true;     // Login / Registro

  // Controllers
  final _emailCtrl = TextEditingController();
  final _passCtrl = TextEditingController();
  final _regEmailCtrl = TextEditingController();
  final _regPassCtrl = TextEditingController();
  final _barberiaNameCtrl = TextEditingController();
  final _ownerNameCtrl = TextEditingController();
  final _phoneCtrl = TextEditingController();
  final _clientNameCtrl = TextEditingController();
  final _clientPhoneCtrl = TextEditingController();

  bool _obscurePass = true;
  bool _obscureRegPass = true;

  @override
  void dispose() {
    _emailCtrl.dispose(); _passCtrl.dispose();
    _regEmailCtrl.dispose(); _regPassCtrl.dispose();
    _barberiaNameCtrl.dispose(); _ownerNameCtrl.dispose();
    _phoneCtrl.dispose(); _clientNameCtrl.dispose();
    _clientPhoneCtrl.dispose();
    super.dispose();
  }

  Future<void> _showForgotPassword(BuildContext context) async {
    final emailCtrl = TextEditingController(text: _emailCtrl.text.trim());
    bool sent = false;

    await showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setModalState) {
          final bottomPadding = MediaQuery.of(ctx).viewInsets.bottom +
              MediaQuery.of(ctx).padding.bottom;

          return Container(
            decoration: const BoxDecoration(
              color: AppColors.graphite,
              borderRadius: AppRadius.rBottomSheet,
            ),
            padding: EdgeInsets.only(bottom: bottomPadding),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Container(
                  margin: const EdgeInsets.only(top: AppSpacing.md),
                  width: 40, height: 4,
                  decoration: BoxDecoration(
                    color: AppColors.smoke, borderRadius: AppRadius.rPill),
                ),
                Padding(
                  padding: const EdgeInsets.all(AppSpacing.base),
                  child: sent
                      ? Column(
                          children: [
                            const Icon(Icons.mark_email_read_outlined,
                              size: 48, color: AppColors.gold),
                            const SizedBox(height: AppSpacing.md),
                            Text('Revisa tu email',
                              style: AppTypography.headingMd),
                            const SizedBox(height: AppSpacing.sm),
                            Text(
                              'Enviamos las instrucciones a ${emailCtrl.text}',
                              style: AppTypography.bodyMd,
                              textAlign: TextAlign.center,
                            ),
                            const SizedBox(height: AppSpacing.xl),
                            AppButton(
                              label: 'Entendido',
                              onTap: () => Navigator.pop(ctx),
                            ),
                            const SizedBox(height: AppSpacing.md),
                          ],
                        )
                      : Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text('Recuperar contraseña',
                              style: AppTypography.headingMd),
                            const SizedBox(height: AppSpacing.xs),
                            Text(
                              'Ingresa tu email y te enviaremos las instrucciones.',
                              style: AppTypography.bodyMd,
                            ),
                            const SizedBox(height: AppSpacing.xl),
                            AppTextField(
                              label: 'Email',
                              controller: emailCtrl,
                              keyboardType: TextInputType.emailAddress,
                              textInputAction: TextInputAction.done,
                              prefixIcon: const Icon(
                                Icons.mail_outline_rounded,
                                size: 18, color: AppColors.ash),
                            ),
                            const SizedBox(height: AppSpacing.xl),
                            AppButton(
                              label: 'Enviar instrucciones',
                              onTap: () async {
                                if (!emailCtrl.text.contains('@')) return;
                                // TODO: conectar con ForgotPasswordUseCase
                                // Por ahora simula el envío
                                await Future.delayed(
                                  const Duration(milliseconds: 800));
                                setModalState(() => sent = true);
                              },
                            ),
                            const SizedBox(height: AppSpacing.md),
                          ],
                        ),
                ),
              ],
            ),
          );
        },
      ),
    );

    emailCtrl.dispose();
  }

  String? _validate() {
    if (_isLoginMode) {
      if (_emailCtrl.text.trim().isEmpty) return 'El email es requerido';
      if (!_emailCtrl.text.contains('@')) return 'Email inválido';
      if (_passCtrl.text.length < 6) return 'Contraseña mínimo 6 caracteres';
      return null;
    }
    if (_isBarberiaTab) {
      if (_barberiaNameCtrl.text.trim().isEmpty) return 'Nombre de barbería requerido';
      if (_ownerNameCtrl.text.trim().isEmpty) return 'Tu nombre es requerido';
      if (_regEmailCtrl.text.trim().isEmpty || !_regEmailCtrl.text.contains('@'))
        return 'Email inválido';
      if (_regPassCtrl.text.length < 8) return 'Contraseña mínimo 8 caracteres';
      return null;
    }
    // Cliente
    if (_clientNameCtrl.text.trim().isEmpty) return 'Tu nombre es requerido';
    if (_clientPhoneCtrl.text.length < 10) return 'Teléfono 10 dígitos';
    if (_regEmailCtrl.text.trim().isEmpty || !_regEmailCtrl.text.contains('@'))
      return 'Email inválido';
    if (_regPassCtrl.text.length < 8) return 'Contraseña mínimo 8 caracteres';
    return null;
  }

  // ─── Submit ──────────────────────────────────────────────────────────────────

  Future<void> _submit() async {
    FocusScope.of(context).unfocus();

    // Validación local primero
    final err = _validate();
    if (err != null) {
      ref.read(authNotifierProvider.notifier).clearError();
      // Mostrar error de validación temporal via state local
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(err), backgroundColor: AppColors.error),
      );
      return;
    }

    final notifier = ref.read(authNotifierProvider.notifier);

    if (_isLoginMode) {
      await notifier.login(
        email: _emailCtrl.text.trim(),
        password: _passCtrl.text,
      );
    } else if (_isBarberiaTab) {
      await notifier.registerOwner(
        email: _regEmailCtrl.text.trim(),
        password: _regPassCtrl.text,
        barberiaName: _barberiaNameCtrl.text.trim(),
        ownerName: _ownerNameCtrl.text.trim(),
        phone: _phoneCtrl.text.trim().isEmpty ? null : _phoneCtrl.text.trim(),
      );
    } else {
      await notifier.registerClient(
        email: _regEmailCtrl.text.trim(),
        password: _regPassCtrl.text,
        name: _clientNameCtrl.text.trim(),
        phone: _clientPhoneCtrl.text.trim(),
      );
    }
  }

  // ─── Build ───────────────────────────────────────────────────────────────────

  @override
  Widget build(BuildContext context) {
    // Escuchar el estado para mostrar errores del servidor
    ref.listen(authNotifierProvider, (prev, next) {
      if (next.failure != null && next.failure != prev?.failure) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(next.failure!.userMessage),
            backgroundColor: AppColors.error,
            behavior: SnackBarBehavior.floating,
            shape: RoundedRectangleBorder(borderRadius: AppRadius.rMd),
          ),
        );
      }
    });

    final isLoading = ref.watch(
      authNotifierProvider.select((s) => s.isLoading),
    );

    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: SystemUiOverlayStyle.light.copyWith(
        statusBarColor: Colors.transparent,
      ),
      child: Scaffold(
        backgroundColor: AppColors.obsidian,
        body: Stack(
          children: [
            _buildBackground(),
            SafeArea(
              child: SingleChildScrollView(
                padding: const EdgeInsets.symmetric(
                  horizontal: AppSpacing.base,
                  vertical: AppSpacing.lg,
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.center,
                  children: [
                    const SizedBox(height: AppSpacing.xl),
                    _buildLogo(),
                    const SizedBox(height: AppSpacing.xxl),
                    _buildTabSelector(),
                    const SizedBox(height: AppSpacing.xl),
                    _buildForm(isLoading),
                    const SizedBox(height: AppSpacing.xxl),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildBackground() {
    return Positioned.fill(
      child: Container(
        decoration: BoxDecoration(
          gradient: RadialGradient(
            center: const Alignment(0, -0.4),
            radius: 1.1,
            colors: [
              AppColors.gold.withOpacity(0.06),
              AppColors.obsidian,
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildLogo() {
    return Column(
      children: [
        const AppLogoMark(size: 60)
            .animate()
            .fadeIn(duration: 500.ms)
            .scale(begin: const Offset(0.8, 0.8), curve: Curves.easeOutBack),
        const SizedBox(height: AppSpacing.md),
        Text(
          'Barbería',
          style: AppTypography.displayMd,
        ).animate().fadeIn(delay: 150.ms),
        const SizedBox(height: 4),
        Text(
          'PREMIUM EXPERIENCE',
          style: AppTypography.overline.copyWith(
            color: AppColors.ash,
            letterSpacing: 0.3,
          ),
        ).animate().fadeIn(delay: 250.ms),
      ],
    );
  }

  Widget _buildTabSelector() {
    return Container(
      padding: const EdgeInsets.all(4),
      decoration: BoxDecoration(
        color: AppColors.graphite,
        borderRadius: AppRadius.rMd,
        border: Border.all(color: AppColors.smoke),
      ),
      child: Row(
        children: [
          _buildTab(
            label: 'Barbería',
            icon: Icons.content_cut_rounded,
            isActive: _isBarberiaTab,
            onTap: () => setState(() {
              _isBarberiaTab = true;
              HapticFeedback.selectionClick();
            }),
          ),
          _buildTab(
            label: 'Cliente',
            icon: Icons.person_outline_rounded,
            isActive: !_isBarberiaTab,
            onTap: () => setState(() {
              _isBarberiaTab = false;
              HapticFeedback.selectionClick();
            }),
          ),
        ],
      ),
    ).animate().fadeIn(delay: 300.ms).slideY(begin: 0.15);
  }

  Widget _buildTab({
    required String label,
    required IconData icon,
    required bool isActive,
    required VoidCallback onTap,
  }) {
    return Expanded(
      child: GestureDetector(
        onTap: onTap,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 220),
          padding: const EdgeInsets.symmetric(vertical: 10),
          decoration: BoxDecoration(
            color: isActive ? AppColors.gold : Colors.transparent,
            borderRadius: AppRadius.rSm,
            boxShadow: isActive ? AppColors.shadowGoldBtn : null,
          ),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(
                icon,
                size: 15,
                color: isActive ? AppColors.obsidian : AppColors.ash,
              ),
              const SizedBox(width: 6),
              Text(
                label,
                style: AppTypography.labelLg.copyWith(
                  color: isActive ? AppColors.obsidian : AppColors.ash,
                  fontWeight: isActive ? FontWeight.w700 : FontWeight.w500,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildForm(bool isLoading) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // Login / Crear cuenta toggle
        _buildModeToggle(),
        const SizedBox(height: AppSpacing.xl),

        // Campos animados
        AnimatedSwitcher(
          duration: const Duration(milliseconds: 260),
          switchInCurve: Curves.easeOutCubic,
          switchOutCurve: Curves.easeInCubic,
          transitionBuilder: (child, anim) => FadeTransition(
            opacity: anim,
            child: SlideTransition(
              position: Tween<Offset>(
                begin: const Offset(0, 0.06),
                end: Offset.zero,
              ).animate(anim),
              child: child,
            ),
          ),
          child: _isLoginMode
              ? _buildLoginFields()
              : (_isBarberiaTab
                  ? _buildRegisterOwnerFields()
                  : _buildRegisterClientFields()),
        ),

        const SizedBox(height: AppSpacing.lg),

        // Botón principal
        AppButton(
          label: _isLoginMode
              ? 'Entrar'
              : (_isBarberiaTab ? 'Crear mi barbería' : 'Crear cuenta'),
          loading: isLoading,
          onTap: isLoading ? null : _submit,
          trailing: isLoading
              ? null
              : const Icon(
                  Icons.arrow_forward_rounded,
                  size: 16,
                  color: AppColors.obsidian,
                ),
        ),

        const SizedBox(height: AppSpacing.xl),
        _buildFooter(),
      ],
    ).animate().fadeIn(delay: 380.ms).slideY(begin: 0.1);
  }

  Widget _buildModeToggle() {
    return Row(
      children: [
        _buildModeOption('Ingresar', _isLoginMode, () {
          setState(() { _isLoginMode = true; });
        }),
        const SizedBox(width: AppSpacing.xl),
        _buildModeOption('Crear cuenta', !_isLoginMode, () {
          setState(() { _isLoginMode = false; });
        }),
      ],
    );
  }

  Widget _buildModeOption(String label, bool isActive, VoidCallback onTap) {
    return GestureDetector(
      onTap: () { HapticFeedback.selectionClick(); onTap(); },
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            label,
            style: AppTypography.headingMd.copyWith(
              color: isActive ? AppColors.white : AppColors.ash,
            ),
          ),
          const SizedBox(height: 4),
          AnimatedContainer(
            duration: const Duration(milliseconds: 200),
            height: 2,
            width: isActive ? 44.0 : 0.0,
            decoration: BoxDecoration(
              gradient: AppColors.goldGradient,
              borderRadius: AppRadius.rPill,
            ),
          ),
        ],
      ),
    );
  }

  // ─── Campos ──────────────────────────────────────────────────────────────────

  Widget _buildLoginFields() {
    return Column(
      key: const ValueKey('login'),
      children: [
        AppTextField(
          label: 'Email',
          hint: 'tu@email.com',
          controller: _emailCtrl,
          keyboardType: TextInputType.emailAddress,
          textInputAction: TextInputAction.next,
          prefixIcon: const Icon(Icons.mail_outline_rounded,
              size: 18, color: AppColors.ash),
        ),
        const SizedBox(height: AppSpacing.md),
        AppTextField(
          label: 'Contraseña',
          controller: _passCtrl,
          obscureText: _obscurePass,
          textInputAction: TextInputAction.done,
          onSubmitted: (_) => _submit(),
          prefixIcon: const Icon(Icons.lock_outline_rounded,
              size: 18, color: AppColors.ash),
          suffixIcon: GestureDetector(
            onTap: () => setState(() => _obscurePass = !_obscurePass),
            child: Icon(
              _obscurePass ? Icons.visibility_off_outlined : Icons.visibility_outlined,
              size: 18, color: AppColors.ash,
            ),
          ),
        ),
        const SizedBox(height: AppSpacing.sm),
        Align(
          alignment: Alignment.centerRight,
          child: TextButton(
            onPressed: () => _showForgotPassword(context),
            child: Text(
              '¿Olvidaste tu contraseña?',
              style: AppTypography.labelMd.copyWith(color: AppColors.gold),
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildRegisterOwnerFields() {
    return Column(
      key: const ValueKey('reg_owner'),
      children: [
        AppTextField(
          label: 'Nombre de tu barbería',
          hint: "Barba's Club",
          controller: _barberiaNameCtrl,
          textInputAction: TextInputAction.next,
          prefixIcon: const Icon(Icons.storefront_outlined,
              size: 18, color: AppColors.ash),
        ),
        const SizedBox(height: AppSpacing.md),
        AppTextField(
          label: 'Tu nombre completo',
          hint: 'Roberto García',
          controller: _ownerNameCtrl,
          textInputAction: TextInputAction.next,
          prefixIcon: const Icon(Icons.person_outline_rounded,
              size: 18, color: AppColors.ash),
        ),
        const SizedBox(height: AppSpacing.md),
        AppTextField(
          label: 'Teléfono (opcional)',
          hint: '5551234567',
          controller: _phoneCtrl,
          keyboardType: TextInputType.phone,
          textInputAction: TextInputAction.next,
          inputFormatters: [FilteringTextInputFormatter.digitsOnly],
          prefixIcon: const Icon(Icons.phone_outlined,
              size: 18, color: AppColors.ash),
        ),
        const SizedBox(height: AppSpacing.md),
        AppTextField(
          label: 'Email',
          hint: 'tu@barberia.com',
          controller: _regEmailCtrl,
          keyboardType: TextInputType.emailAddress,
          textInputAction: TextInputAction.next,
          prefixIcon: const Icon(Icons.mail_outline_rounded,
              size: 18, color: AppColors.ash),
        ),
        const SizedBox(height: AppSpacing.md),
        AppTextField(
          label: 'Contraseña',
          hint: 'Mínimo 8 caracteres',
          controller: _regPassCtrl,
          obscureText: _obscureRegPass,
          textInputAction: TextInputAction.done,
          onSubmitted: (_) => _submit(),
          prefixIcon: const Icon(Icons.lock_outline_rounded,
              size: 18, color: AppColors.ash),
          suffixIcon: GestureDetector(
            onTap: () => setState(() => _obscureRegPass = !_obscureRegPass),
            child: Icon(
              _obscureRegPass ? Icons.visibility_off_outlined : Icons.visibility_outlined,
              size: 18, color: AppColors.ash,
            ),
          ),
        ),
        const SizedBox(height: AppSpacing.md),
        GoldGlowContainer(
          child: Row(
            children: [
              Container(
                width: 32, height: 32,
                decoration: BoxDecoration(
                  color: AppColors.goldSubtle,
                  borderRadius: AppRadius.rSm,
                ),
                child: const Icon(
                  Icons.star_outline_rounded,
                  color: AppColors.gold, size: 18,
                ),
              ),
              const SizedBox(width: AppSpacing.md),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Plan FREE por 30 días',
                      style: AppTypography.labelLg.copyWith(color: AppColors.white)),
                    Text('Sin comisiones durante el primer mes',
                      style: AppTypography.bodySm),
                  ],
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildRegisterClientFields() {
    return Column(
      key: const ValueKey('reg_client'),
      children: [
        AppTextField(
          label: 'Tu nombre',
          hint: 'Juan Pérez',
          controller: _clientNameCtrl,
          textInputAction: TextInputAction.next,
          prefixIcon: const Icon(Icons.person_outline_rounded,
              size: 18, color: AppColors.ash),
        ),
        const SizedBox(height: AppSpacing.md),
        AppTextField(
          label: 'Teléfono',
          hint: '5551234567',
          controller: _clientPhoneCtrl,
          keyboardType: TextInputType.phone,
          textInputAction: TextInputAction.next,
          inputFormatters: [FilteringTextInputFormatter.digitsOnly],
          prefixIcon: const Icon(Icons.phone_outlined,
              size: 18, color: AppColors.ash),
        ),
        const SizedBox(height: AppSpacing.md),
        AppTextField(
          label: 'Email',
          hint: 'tu@email.com',
          controller: _regEmailCtrl,
          keyboardType: TextInputType.emailAddress,
          textInputAction: TextInputAction.next,
          prefixIcon: const Icon(Icons.mail_outline_rounded,
              size: 18, color: AppColors.ash),
        ),
        const SizedBox(height: AppSpacing.md),
        AppTextField(
          label: 'Contraseña',
          hint: 'Mínimo 8 caracteres',
          controller: _regPassCtrl,
          obscureText: _obscureRegPass,
          textInputAction: TextInputAction.done,
          onSubmitted: (_) => _submit(),
          prefixIcon: const Icon(Icons.lock_outline_rounded,
              size: 18, color: AppColors.ash),
          suffixIcon: GestureDetector(
            onTap: () => setState(() => _obscureRegPass = !_obscureRegPass),
            child: Icon(
              _obscureRegPass ? Icons.visibility_off_outlined : Icons.visibility_outlined,
              size: 18, color: AppColors.ash,
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildFooter() {
    return Center(
      child: GestureDetector(
        onTap: () => setState(() {
          _isLoginMode = !_isLoginMode;
          HapticFeedback.lightImpact();
        }),
        child: RichText(
          text: TextSpan(
            style: AppTypography.bodySm.copyWith(color: AppColors.ash),
            children: [
              TextSpan(
                text: _isLoginMode
                    ? '¿Sin cuenta aún? '
                    : '¿Ya tienes cuenta? ',
              ),
              TextSpan(
                text: _isLoginMode ? 'Crear cuenta' : 'Ingresar',
                style: AppTypography.bodySm.copyWith(
                  color: AppColors.gold,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
