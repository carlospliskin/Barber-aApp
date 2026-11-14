import 'package:freezed_annotation/freezed_annotation.dart';
import '../../domain/entities/auth_user.dart';

part 'auth_model.freezed.dart';
part 'auth_model.g.dart';

// ─── Modelos de data — con serialización ──────────────────────────────────────
// Solo existen en la capa data. Domain nunca los ve.

@freezed
class TenantModel with _$TenantModel {
  const factory TenantModel({
    required String id,
    required String name,
    @Default('America/Mexico_City') String timezone,
    @Default('FREE') String plan,
  }) = _TenantModel;

  factory TenantModel.fromJson(Map<String, dynamic> json) =>
      _$TenantModelFromJson(json);
}

@freezed
class ClientProfileModel with _$ClientProfileModel {
  const factory ClientProfileModel({
    required String id,
    required String name,
    String? phone,
    @Default(false) bool isPenalized,
    String? penaltyUntil,
    @Default(0) int noShowCount,
  }) = _ClientProfileModel;

  factory ClientProfileModel.fromJson(Map<String, dynamic> json) =>
      _$ClientProfileModelFromJson(json);
}

@freezed
class AuthPayloadModel with _$AuthPayloadModel {
  const factory AuthPayloadModel({
    required String token,
    required String refreshToken,
    required String userId,
    required List<String> capabilities,
    @Default('CLIENT') String primaryRole,
    TenantModel? tenant,
    ClientProfileModel? clientProfile,
  }) = _AuthPayloadModel;

  factory AuthPayloadModel.fromJson(Map<String, dynamic> json) =>
      _$AuthPayloadModelFromJson(json);
}

// ─── Mapper Model → Domain entity ─────────────────────────────────────────────

extension AuthPayloadModelX on AuthPayloadModel {
  AuthUser toDomain() => AuthUser(
    userId: userId,
    token: token,
    refreshToken: refreshToken,
    capabilities: capabilities,
    primaryRole: primaryRole,
    tenant: tenant?.toDomain(),
    clientProfile: clientProfile?.toDomain(),
  );
}

extension TenantModelX on TenantModel {
  TenantInfo toDomain() => TenantInfo(
    id: id,
    name: name,
    timezone: timezone,
    plan: plan,
  );
}

extension ClientProfileModelX on ClientProfileModel {
  ClientProfileInfo toDomain() => ClientProfileInfo(
    id: id,
    name: name,
    phone: phone,
    isPenalized: isPenalized,
    penaltyUntil: penaltyUntil,
    noShowCount: noShowCount,
  );
}
